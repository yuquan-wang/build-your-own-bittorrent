import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AsciiString;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackerServer {

    static final int PORT = 6881;

    // key is info_hash(每个torrent都不同), value is list peers, containing key ip and port
    // 注意Tracker是把整个集群的信息都保存在内存中的，也不用担心内存的不可靠性，因为torrentclient是每隔10/15秒就会进行announce,然后tracker就可以在一个进行内存更新
    private static final Map<String, List<Map<String, String>>> TORRENT_MAP = new HashMap<>();

    public void start() throws InterruptedException {
        // Configure the server.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new HttpTrackerServerInitializer());

            Channel ch = b.bind(PORT).sync().channel();

            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    static class HttpTrackerServerInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline p = ch.pipeline();
            p.addLast(new HttpServerCodec());
            p.addLast(new HttpServerExpectContinueHandler());
            p.addLast(new HttpTrackerServerHandler());
        }
    }

    static class HttpTrackerServerHandler extends SimpleChannelInboundHandler<HttpObject> {

        private static final AsciiString CONTENT_TYPE = AsciiString.cached("Content-Type");
        private static final AsciiString CONTENT_LENGTH = AsciiString.cached("Content-Length");

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx){
            ctx.flush();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            if(msg instanceof HttpRequest){
                HttpRequest req = (HttpRequest)msg;
                String requestUri = req.uri();
                FullHttpResponse response;
                if(requestUri.startsWith("/announce")){  //只处理announce请求
                    String ipAddress = getIpAddress(ctx);
                    System.out.println("request from: " + ipAddress );
                    List<NameValuePair> params = URLEncodedUtils.parse(new URI(requestUri), Charset.forName("UTF-8"));
                    String port = "";
                    String info_hash = "";
                    for (NameValuePair param : params) {
                        System.out.println(param.getName() + " : " + param.getValue());
                        if(param.getName().equals("port")){
                            port = param.getValue(); //拿到请求中的port参数
                        }else if(param.getName().equals("info_hash")) {
                            info_hash = param.getValue(); //拿到请求中的info_hash参数
                        }
                    }
                    if(port.isEmpty()||info_hash.isEmpty()) {
                        response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST,
                                Unpooled.wrappedBuffer("Please pass info_hash or port".getBytes()));
                    }else{
                        List<Map<String,String> > peers = new ArrayList<>();
                        if(TORRENT_MAP.containsKey(info_hash)){
                            peers = TORRENT_MAP.get(info_hash); //返回当前有哪些peer函数这个数据
                        }
                        Bencode bencode = new Bencode();
                        response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                                Unpooled.wrappedBuffer(bencode.encode(peers).getBytes()));
                        addToTorrentMap(info_hash, ipAddress, port); //最后要把当前peer也加入到Tracker的追踪map中
                    }
                }else{
                    response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND,
                            Unpooled.wrappedBuffer("Not supported".getBytes()));
                }

                response.headers().set(CONTENT_TYPE, "text/plain");
                response.headers().setInt(CONTENT_LENGTH, response.content().readableBytes());
                ctx.write(response).addListener(ChannelFutureListener.CLOSE);
            }
        }

        private void addToTorrentMap(String info_hash, String ipAddress, String port) {
            if(!TORRENT_MAP.containsKey(info_hash)) {
                TORRENT_MAP.put(info_hash, new ArrayList<>());
            }
            List<Map<String, String>> peers = TORRENT_MAP.get(info_hash);
            boolean exist = false;
            for(Map<String, String> peer: peers){
                if(peer.get("ip").equals(ipAddress) && peer.get("port").equals(port)) {
                    exist = true;
                }
            }
            if(!exist){
                Map<String,String> newPeer = new HashMap<>();
                newPeer.put("ip", ipAddress);
                newPeer.put("port", port);
                peers.add(newPeer);
            }
        }

        private String getIpAddress(ChannelHandlerContext ctx){
            InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            InetAddress inetaddress = socketAddress.getAddress();
            String ipAddress = inetaddress.getHostAddress(); // IP address of client
            return ipAddress;
        }

    }

    public static void main(String[] args) throws InterruptedException {
        TrackerServer trackerServer = new TrackerServer();
        trackerServer.start();
    }
}

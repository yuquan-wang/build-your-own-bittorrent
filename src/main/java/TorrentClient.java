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
import javafx.util.Pair;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;

public class TorrentClient {
    private Torrent torrent; //对应的torrent
    private TrackerClient trackerClient = new TrackerClient(); // 用来汇报给tracker
    private File location; //数据所在位置,ifFile()==true代表是seed状态，若传入一个文件夹则表示要下载到该文件夹下
    private State state; //代表当前状态是seeding还是downloading
    private BitSet downloaded; //用来记录哪些piece被下载了
    private List<Map<String, String>> peers = new ArrayList<>();//记录Tracker的回复，也就是该问哪些peer去要数据
    private int port = new Random().nextInt(1000) + 3000; //当前做种的端口
    final static CloseableHttpClient httpClient = HttpClients.createDefault(); //问其他peer要数据用

    public TorrentClient(Torrent torrent, File location) {
        this.torrent = torrent;
        this.location = location;
        if (location.isFile()) {
            state = State.seeding; //若传进来一文件，我们认为是直接做种
        } else {
            state = State.downloading;//否则我们认为是需要下载到当前文件夹下(更好的办法是对数据进行分析，如果完全符合torrent则是seeding,不符合则是downloading)
            try {
                File targetFile = new File(location, torrent.getName());
                targetFile.createNewFile();
                RandomAccessFile raf = new RandomAccessFile(targetFile, "rw");
                raf.setLength(torrent.getLength());
            } catch (IOException e) {
                System.err.println("Failed to create target file for torrent");
            }
        }
        downloaded = new BitSet(torrent.getPieceLength());
    }

    private void startAnnounceThread() {
        //不管是下载还是做种，都需要进行announce
        AnnounceThread announceThread = new AnnounceThread();
        announceThread.start();
    }

    public void start() {
        startAnnounceThread();
        if (state == State.seeding) {
            seed();
        } else {
            download();
        }
    }

    private void seed() {
        SeedThread seedThread = new SeedThread();
        seedThread.start();
    }

    //blocking implementation, of course we could change it to non blocking and add call back after download
    private void download() {
        DownloadThread downloadThread = new DownloadThread();
        downloadThread.start();

    }

    private Pair<String, String> pickPeerForDownload() {
        if (peers.isEmpty()) {
            return null;
        }
        int n = peers.size();
        Map<String, String> peer =  peers.get(new Random().nextInt(n));
        return new Pair(peer.get("ip"), peer.get("port"));
    }

    private Boolean downloadPiece(Pair<String, String> peer, int undownloadedPiece){
        try {
            String host = peer.getKey();
            String port = peer.getValue();
            HttpGet httpget = new HttpGet("http://"+ host + ":" + port + "/piece?q=" + undownloadedPiece);
            CloseableHttpResponse response = httpClient.execute(httpget);
            HttpEntity entity = response.getEntity();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            entity.writeTo(baos);
            byte[] contents = baos.toByteArray();
            byte[] sha1 = DigestUtils.sha1(contents);
            String downloadedPiece = new String(sha1, Charset.forName("UTF-16"));
            int skippedLength = 10* undownloadedPiece ; // each piece responding to 10 bytes
            String pieceInTorrent = torrent.getPieces().substring(skippedLength, skippedLength+Math.min(torrent.getPieces().length() - skippedLength, 10));
            if (pieceInTorrent.equals(downloadedPiece)) { //下载完要进行hash验证，以防做种方出错或者网络出错
                //successful download
                try(RandomAccessFile raf = new RandomAccessFile( new File(location, torrent.getName()), "rw");) {
                    raf.skipBytes(torrent.getPieceLength()*undownloadedPiece);
                    raf.write(contents);
                }
                System.out.println("Successfully downloaded piece "  + undownloadedPiece + " from peer " + peer);
                return true;
            } else {
                //downloaded content is not what we want
                return false;
            }
        }catch(IOException e){
            return false;
        }
    }

    private Boolean isDownloadFinished() {
        int pieceNumber = (int)Math.ceil(1.0* torrent.getLength()/torrent.getPieceLength());
        for (int i = 0; i < pieceNumber; i++) {
            if (!downloaded.get(i)) {
                return false;
            }
        }
        return true;
    }

    private int pickUndownloadedPiece() {
        for (int i = 0; i < torrent.getPieceLength(); i++) {
            if (!downloaded.get(i))
                return i;
        }
        return -1;
    }

    public class AnnounceThread extends Thread {

        @Override
        public void run() {
            while (true) {
                try {
                    peers = trackerClient.announceToTracker(torrent.getInfoHash(), port);
                    Thread.sleep(15 * 1000);
                } catch (InterruptedException e) {
                    Thread.interrupted();
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public class DownloadThread extends Thread{
        @Override
        public void run(){
            int failCnt = 0; // return false when fail more than 10 times
            while (!isDownloadFinished()) {
                int undownloadedPiece = pickUndownloadedPiece();
                Pair<String, String> peer = pickPeerForDownload();
                if (peer != null && downloadPiece(peer, undownloadedPiece)) {
                    downloaded.set(undownloadedPiece);
                    failCnt = 0;
                } else {
                    failCnt++;
                }
                if (failCnt > 10) {
                    System.err.println("Failed to download. Exit");
                    return;
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Download finished...Yeah!");
        }
    }

    public class SeedThread extends Thread {
        @Override
        public void run() {
            EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.option(ChannelOption.SO_BACKLOG, 1024);
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(new SeedingServerInitializer());

                Channel ch = b.bind(port).sync().channel();

                ch.closeFuture().sync();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }


        class SeedingServerInitializer extends ChannelInitializer<SocketChannel> {

            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new HttpServerCodec());
                p.addLast(new HttpServerExpectContinueHandler());
                p.addLast(new SeedingServerHandler());
            }
        }

        class SeedingServerHandler extends SimpleChannelInboundHandler<HttpObject> {

            private final AsciiString CONTENT_TYPE = AsciiString.cached("Content-Type");
            private final AsciiString CONTENT_LENGTH = AsciiString.cached("Content-Length");
            private final Map<String, List<Pair<String, String>>> torrentMap = new HashMap<>(); // key info_hash, value is list peers, containing key ip and port


            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                ctx.flush();
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
                if (msg instanceof HttpRequest) {
                    HttpRequest req = (HttpRequest) msg;
                    String requestUri = req.uri();
                    FullHttpResponse response;
                    if (requestUri.startsWith("/piece")) {
                        List<NameValuePair> params = URLEncodedUtils.parse(new URI(requestUri), Charset.forName("UTF-8"));
                        String requestedPiece = "";
                        for (NameValuePair param : params) {
                            if (param.getName().equals("q")) { //获取请求的piece
                                requestedPiece = param.getValue();
                            }
                        }
                        if (requestedPiece.isEmpty()) {
                            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                                    Unpooled.wrappedBuffer("Please pass info_hash or port".getBytes()));
                        } else {
                            int pieceIndex = Integer.valueOf(requestedPiece);
                            int pieceLength = torrent.getPieceLength();
                            FileInputStream fileInputStream = new FileInputStream(location);
                            fileInputStream.skip(pieceLength * pieceIndex); //从数据文件中定位请求的数据，然后以byte array发送回去
                            byte[] contents = IOUtils.toByteArray(fileInputStream, pieceLength * (pieceIndex + 1) > torrent.getLength() ? torrent.getLength() - pieceLength * pieceIndex : pieceLength);
                            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                                    Unpooled.wrappedBuffer(contents));

                        }
                    } else {
                        response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND,
                                Unpooled.wrappedBuffer("Not supported".getBytes()));
                    }

                    response.headers().set(CONTENT_TYPE, "text/plain");
                    response.headers().setInt(CONTENT_LENGTH, response.content().readableBytes());
                    ctx.write(response).addListener(ChannelFutureListener.CLOSE);
                }
            }
        }

    }


    public enum State {
        downloading,
        seeding
    }
}

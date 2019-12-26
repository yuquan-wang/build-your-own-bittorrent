import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TorrentClientTest {

    public static void main(String[] args) throws Exception {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        Path source = Paths.get(classloader.getResource("青花瓷.mp3").toURI());
        TorrentUtil.createTorrent(source.toFile(), source.getParent().toFile());

        Torrent t = TorrentUtil.loadTorrentFromFile(new File(source.getParent().toFile().getAbsolutePath() + "/青花瓷.mp3.torrent"));

        File target = new File("/tmp/testClient");
        target.mkdir();

        Thread trackerThread = new Thread(){
            public void run(){
                TrackerServer trackerServer = new TrackerServer();
                try {
                    trackerServer.start();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        trackerThread.start();
        Thread.sleep(5000);

        TorrentClient torrentClient1 = new TorrentClient(t, source.toFile());
        TorrentClient torrentClient2 = new TorrentClient(t, target);
        torrentClient1.start();
        torrentClient2.start();
        Thread.sleep(1000000);
    }
}

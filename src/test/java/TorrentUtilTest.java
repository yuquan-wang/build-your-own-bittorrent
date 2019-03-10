import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class TorrentUtilTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testCreateTorrentAndLoadTorrent() throws Exception {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        Path source = Paths.get(classloader.getResource("青花瓷.mp3").toURI());
        TorrentUtil.createTorrent(source.toFile(), folder.getRoot());

        Torrent t = TorrentUtil.loadTorrentFromFile(new File(folder.getRoot().getAbsolutePath() + "/青花瓷.mp3.torrent"));
        assertEquals("青花瓷.mp3", t.getName());
        assertEquals("localhost:6881", t.getAnnounce());
        assertEquals(3843256, t.getLength());
        assertEquals(1048576, t.getPieceLength());
    }
}

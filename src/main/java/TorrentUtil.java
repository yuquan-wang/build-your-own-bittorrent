import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.util.Map;

public class TorrentUtil {
    private final static String ANNOUNCE = "localhost:6881";
    private final static int PIECE_LENGTH = 1048576; // 2^20, 1MB

    /**
     * @param file the source file to make a torrent, current we only support single file
     * @param dest create a torrent file under dest, torrent name will be same as file's name
     * @throws Exception
     */
    public static void createTorrent(File file, File dest) throws Exception {
        Assert.assertTrue("Torrent source should be a file", file.isFile());
        Assert.assertTrue("Torrent destination should be a directory", dest.isDirectory());

        String name = file.getName();
        int length = (int) file.length();
        int len = length;
        String pieces = "";
        FileInputStream fileInputStream = new FileInputStream(file);
        while (len > 0) {
            long nextPieceLength = len >= PIECE_LENGTH ? PIECE_LENGTH : len;

            byte[] contents = IOUtils.toByteArray(fileInputStream, nextPieceLength);
            byte[] sha1 = DigestUtils.sha1(contents);
            pieces += new String(sha1, Charset.forName("UTF-16"));

            len -= nextPieceLength;
        }

        File torrentFile = new File(dest, name + ".torrent");
        torrentFile.createNewFile();
        Bencode bencode = new Bencode();
        String torrentContent = bencode.encode(new Torrent(ANNOUNCE, name, PIECE_LENGTH, length, pieces).torrentToMap());
        FileUtils.writeStringToFile(torrentFile, torrentContent, Charset.defaultCharset());
    }

    public static Torrent loadTorrentFromFile(File torrentLocation) throws Exception {
        Assert.assertTrue(torrentLocation.exists());

        String torrentContent = FileUtils.readFileToString(torrentLocation, Charset.defaultCharset());
        Bencode bencode = new Bencode();
        Map<String, Object> contentMap = (Map<String, Object>) bencode.decode(torrentContent);
        return new Torrent(
                (String) contentMap.get(Torrent.ANNOUNCE_FIELD),
                (String) ((Map<String, Object>) contentMap.get(Torrent.INFO_FIELD)).get(Torrent.NAME_FIELD),
                (Integer) ((Map<String, Object>) contentMap.get(Torrent.INFO_FIELD)).get(Torrent.PIECE_LENGTH_FIELD),
                (Integer) ((Map<String, Object>) contentMap.get(Torrent.INFO_FIELD)).get(Torrent.LENGTH_FIELD),
                (String) ((Map<String, Object>) contentMap.get(Torrent.INFO_FIELD)).get(Torrent.PIECES_FILED));
    }

}

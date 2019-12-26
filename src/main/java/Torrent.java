
import org.apache.commons.codec.digest.DigestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Torrent {
    static String ANNOUNCE_FIELD = "announce";
    static String NAME_FIELD = "name";
    static String PIECE_LENGTH_FIELD = "pieceLength";
    static String LENGTH_FIELD = "length";
    static String PIECES_FILED = "pieces";
    static String INFO_FIELD = "info";

    private String announce;
    private String name;
    private int pieceLength;
    private int length;
    private String pieces;

    public Torrent(String announce, String name, int pieceLength, int length, String pieces) {
        this.announce = announce;
        this.name = name;
        this.pieceLength = pieceLength;
        this.length = length;
        this.pieces = pieces;
    }

    Map<String, Object> torrentToMap() {
        Map<String, Object> m = new HashMap<>();
        m.put(ANNOUNCE_FIELD, announce);
        Map<String, Object> info = new HashMap();
        info.put(NAME_FIELD, name);
        info.put(PIECE_LENGTH_FIELD, pieceLength);
        info.put(LENGTH_FIELD, length);
        info.put(PIECES_FILED, pieces);
        m.put(INFO_FIELD, info);
        return m;
    }

    public String getInfoHash() {
        return DigestUtils.md5Hex(torrentToMap().toString());
    }

    public String getAnnounce() {
        return announce;
    }

    public void setAnnounce(String announce) {
        this.announce = announce;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPieceLength() {
        return pieceLength;
    }

    public void setPieceLength(int pieceLength) {
        this.pieceLength = pieceLength;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public String getPieces() {
        return pieces;
    }

    public void setPieces(String pieces) {
        this.pieces = pieces;
    }


}

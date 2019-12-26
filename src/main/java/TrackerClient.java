
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.util.List;
import java.util.Map;

public class TrackerClient {
    final static String TRACKER_URI = "http://localhost:6881/announce";
    final static CloseableHttpClient httpClient = HttpClients.createDefault();
    final static String PORT = "port";
    final static String INFO_HASH = "info_hash";

    public List<Map<String, String>> announceToTracker(String infoHash, int port) throws Exception {
        HttpGet httpget = new HttpGet(TRACKER_URI + "?" + PORT + "=" + port + "&" + INFO_HASH + "=" + infoHash);
        CloseableHttpResponse response = httpClient.execute(httpget);
        String responseString = IOUtils.toString(response.getEntity().getContent());
        Bencode bencode = new Bencode();
        List<Map<String, String>> peers = (List<Map<String, String>>) bencode.decode(responseString);
        return peers;
    }
}

package homecontrol;

import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.amazonaws.util.json.JSONTokener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author harri
 */
public class HttpRequest {
    private static final Logger log = LoggerFactory.getLogger(HttpRequest.class);
    private static final String TOKEN = System.getenv("token");
    private static final String ENDPOINT = System.getenv("host") + "Rest/HomeService.svc/";
    public static final String CONNECTION_FAILURE_TEXT = "Failed to connect to web service.";
    
    public HttpRequest() {
        try {
            initTrustManager();
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            log.error("Failed to initialize trust manager.", e);
        }
    }

    public Map<String, String> send(String path) {
        return send(path, null);
    }

    public Map<String, String> send(String path, Map<String, String> parameters) {
        Map<String, String> response = null;

        String queryString = query(parameters);
        InputStreamReader inputStream = null;
        BufferedReader bufferedReader = null;
        StringBuilder builder = new StringBuilder();
        try {
            String line;
            URL url = new URL(ENDPOINT + path + queryString);
            inputStream = new InputStreamReader(url.openStream());
            bufferedReader = new BufferedReader(inputStream);
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }
        } catch (Exception e) {
            // reset builder to a blank string
            log.error("Failed to call web service.", e);
            builder.setLength(0);
        } finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(bufferedReader);
        }

        if (builder.length() != 0) {
            response = new HashMap<>();
            try {
                JSONObject responseObject = new JSONObject(new JSONTokener(builder.toString()));
                Iterator<String> iterator = responseObject.sortedKeys();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    String value = responseObject.getString(key);
                    response.put(key, value);
                }
            } catch (JSONException e) {
                log.error("Failed to parse service response.", e);
            }
        }

        return response;
    }

    private String query(Map<String, String> parameters) {
        StringBuilder sb = new StringBuilder("?");
        sb.append("token=");
        sb.append(TOKEN);

        if (parameters != null) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                sb.append("&");
                sb.append(entry.getKey());
                sb.append("=");
                sb.append(entry.getValue());
            }
        }

        return sb.toString();
    }

    private void initTrustManager() throws KeyManagementException, NoSuchAlgorithmException {
        // Create a trust manager that does not validate certificate chains
        final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
            }

            @Override
            public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        }};

        // Install the all-trusting trust manager
        final SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, null);
        // Create an ssl socket factory with our all-trusting manager
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            public boolean verify(String urlHostName, SSLSession session) {
                return true;
            }
        });
    }
}

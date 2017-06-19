/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package location;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.amazonaws.util.json.JSONTokener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Utility class to read addresses from Google profile. Currently Amazon Alexa
 * application only allows to configure addresses in US, UK and Germany. Google
 * allows to configure home and work addresses and has no restrictions where the
 * address is located. This utility requires that the profile has been set
 * public in order to read it.
 *
 * @author HHY
 */
public class Google {

    private static final Logger LOG = LoggerFactory.getLogger(Google.class);
    private static final String URL = "https://people.googleapis.com/v1/people/me?requestMask.includeField=person.addresses";
    private static final String URL_GEOCODE = "http://maps.google.com/maps/api/geocode/xml?address=";

    private Google() {
    }

    /**
     * Get addresses entered in Google profile.
     *
     * @param token Google OAuth token
     * @param addressType Google profile supports at least "home" and "work"
     * @return Map of addresses found in Google profile. Address types are in
     * map keys and address texts are in values.
     */
    public static Map<String, String> getAddressFromGoogleProfile(final String token, final String addressType) {
        Map<String, String> ret = new HashMap<>();

        StringBuffer response = null;
        try {
            URL obj = new URL(URL);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // optional default is GET
            con.setRequestMethod("GET");

            //add authentication
            con.setRequestProperty("Authorization", "Bearer " + token);

            int responseCode = con.getResponseCode();

            if (responseCode == 200) {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()))) {
                    String inputLine;
                    response = new StringBuffer();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                }
            }
        } catch (IOException ex) {
            LOG.error("Failed to send GET request: ", ex);
        }

        if (response != null && response.length() != 0) {
            try {
                JSONObject responseObject = new JSONObject(new JSONTokener(response.toString()));

                JSONArray addresses = responseObject.getJSONArray("addresses");
                for (int i = 0; i < addresses.length(); i++) {
                    JSONObject address = addresses.getJSONObject(i);
                    String type = address.getString("type");
                    String text = address.getString("formattedValue");
                    ret.put(type, text);
                }
            } catch (JSONException ex) {
                LOG.error("Failed to parse service response.", ex);
            }
        }

        return ret;
    }

    /**
     * Get address location.
     *
     * @param address Address text
     * @return Location in format: latitude,longitude
     */
    public static String getLocationFromAddress(final String address) {
        String location = null;
        String url = URL_GEOCODE + address.replace(' ', '+');

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(url);
            doc.getDocumentElement().normalize();

            Element locElement = (Element) doc.getElementsByTagName("location").item(0);
            String lat = locElement.getElementsByTagName("lat").item(0).getTextContent();
            String lon = locElement.getElementsByTagName("lng").item(0).getTextContent();

            if (lat != null && lon != null) {
                location = lat + "," + lon;
            }
        } catch (ParserConfigurationException | DOMException | SAXException | IOException ex) {
            // reset builder to a blank string
            LOG.error("Failed to read observations.", ex);
        }

        return location;
    }

    public static String getCity(final String address) {
        String city = null;

        // expects that address format is [<street>,]<city>
        String tokens[] = address.split(",");
        if(tokens.length == 1) {
            city = tokens[0];
        } else if (tokens.length > 1) {
            city = tokens[tokens.length - 1].trim();
        }

        return city;
    }
}

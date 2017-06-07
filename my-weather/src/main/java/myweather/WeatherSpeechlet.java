package myweather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.amazonaws.util.json.JSONTokener;
import homecontrol.HttpRequest;
import homecontrol.Responder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class WeatherSpeechlet implements Speechlet {

    private static final Logger LOG = LoggerFactory.getLogger(WeatherSpeechlet.class);

    private static final String CARD_TITLE = "My Weather";
    private final HttpRequest wsRequest = new HttpRequest();
    private final Responder responder = new Responder(CARD_TITLE);
    private static final String SESSION_ISWHATNEXT = "isWhatNext";
    private static final String APIKEY = System.getenv("api_key");
    private static final String URL_OBSERVATIONS = "http://data.fmi.fi/fmi-apikey/" + APIKEY
            + "/wfs?request=getFeature&storedquery_id=fmi::observations::weather::multipointcoverage"; 
    private static final String URL_FORECASTS = "http://data.fmi.fi/fmi-apikey/" + APIKEY
            + "/wfs?request=getFeature&storedquery_id=fmi::forecast::hirlam::surface::point::multipointcoverage"; // &latlon=60.18553,24.61650&starttime=2017-06-06T13:00:00Z&endtime=2017-06-06T15:00:00Z";
    private static final String SLOT_TIME = "time";
    private static final String SLOT_LOCATION = "location";
    private static final String SLOT_LOCATION_DEFAULT_VALUE = "home";
    private static final Map<String, Integer> TIMES = new HashMap<>();

    static {
        TIMES.put("morning", 6);
        TIMES.put("noon", 12);
        TIMES.put("afternoon", 15);
        TIMES.put("evening", 18);
        TIMES.put("night", 21);
        TIMES.put("tomorrow morning", 30);
        TIMES.put("tomorrow noon", 36);
        TIMES.put("tomorrow afternoon", 39);
        TIMES.put("tomorrow evening", 42);
        TIMES.put("tomorrow night", 45);
    }
    
    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        LOG.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // any initialization logic goes here
        session.setAttribute(SESSION_ISWHATNEXT, false);
    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        LOG.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        LOG.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        session.setAttribute(SESSION_ISWHATNEXT, true);
        return getWelcomeResponse();
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        LOG.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        Intent intent = request.getIntent();
        String intentName = (intent != null) ? intent.getName() : null;

        if ("CurrentWeatherIntent".equals(intentName)) {
            return handleCurrentWeatherRequest(intent, session);
        } else if ("ForecastedWeatherIntent".equals(intentName)) {
            return handleForecastedWeatherRequest(intent, session);
        } else if ("AMAZON.HelpIntent".equals(intentName)) {
            return handleHelpRequest();
        } else if ("AMAZON.StopIntent".equals(intentName)) {
            return responder.tellResponse("Goodbye");
        } else if ("AMAZON.CancelIntent".equals(intentName)) {
            return responder.tellResponse("Goodbye");
        } else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    private SpeechletResponse getWelcomeResponse() {
        return responder.askResponse("Ready");
    }

    private SpeechletResponse handleHelpRequest() {
        String speechText = "You can ask " + CARD_TITLE + " about movies.";

        return responder.askResponse(speechText);
    }

    private SpeechletResponse handleCurrentWeatherRequest(final Intent intent, final Session session) {
        String location = SLOT_LOCATION_DEFAULT_VALUE;
        if(intent.getSlot(SLOT_LOCATION) != null && intent.getSlot(SLOT_LOCATION).getValue() != null) {
            location = intent.getSlot(SLOT_LOCATION).getValue();
        }
        
        String token = session.getUser().getAccessToken();
        String address = getAddressFromGoogleProfile(token, location);
        String city = getCity(address);
        
        StringBuilder sb = new StringBuilder();
        sb.append("Observations in ");
        sb.append(city);
        sb.append(" are: ");

        LocalDateTime datetime = LocalDateTime.now(ZoneId.of("Z")).minusHours(1).withMinute(0).withSecond(0).withNano(0);
        Map<String, String> observations = getObservations(city, datetime);

        boolean isFirst = true;
        for(String key : observations.keySet()) {
            if(!isFirst) {
                sb.append(", ");
            } else {
                isFirst = false;
            }
            sb.append(key);
            sb.append(" ");
            sb.append(observations.get(key));
        }
        
        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(sb.toString(), isWhatNext);
    }

    private SpeechletResponse handleForecastedWeatherRequest(final Intent intent, final Session session) {
        StringBuilder sb = new StringBuilder();
        sb.append("Forecast is: ");

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(sb.toString(), isWhatNext);
    }

    private Map<String, String> getObservations(String city, LocalDateTime datetime) {
        Map<String, String> observations = new HashMap<>();

        // &place=espoo&starttime=2017-06-06T13:00:00Z&endtime=2017-06-06T13:00:00Z";
        String url = URL_OBSERVATIONS + 
                "&place=" + city +
                "&starttime=" + datetime.format(DateTimeFormatter.ISO_DATE_TIME) +
                "&endtime=" + datetime.format(DateTimeFormatter.ISO_DATE_TIME);
        
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(url);
            doc.getDocumentElement().normalize();

            Element list = (Element) doc.getElementsByTagName("gml:doubleOrNilReasonTupleList").item(0);
            String obsList = list.getTextContent();
            String values[] = obsList.trim().split(" ");

            List<String> fieldNames = new ArrayList<>();
            Element dataRecord = (Element) doc.getElementsByTagName("swe:DataRecord").item(0);
            NodeList fields = dataRecord.getElementsByTagName("swe:field");
            for(int i = 0; i < fields.getLength(); i++) {
                Element field = (Element)fields.item(i);
                String name = field.getAttribute("name");
                fieldNames.add(name);
            }

            setObservations(observations, values, fieldNames);
        } catch (ParserConfigurationException | DOMException | SAXException | IOException e) {
            // reset builder to a blank string
            System.out.println("Failed to read observations.");
        }

        return observations;
    }

    private void setObservations(Map<String, String> observations, String values[], List<String> fieldNames) {
        // TODO
    }
    
    private String getAddressFromGoogleProfile(String token, String addressType) {
        String ret = null;
        StringBuffer response = null;
        try {
            URL obj = new URL("https://people.googleapis.com/v1/people/me?requestMask.includeField=person.names,person.addresses");
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // optional default is GET
            con.setRequestMethod("GET");

            //add authentication
            con.setRequestProperty("Authorization", "Bearer " + token);
            LOG.info("Token is : " + token);

            int responseCode = con.getResponseCode();
            LOG.info("Response Code : " + responseCode);

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
                    if (type.equals(addressType)) {
                        ret = text;
                        break;
                    }
                }
            } catch (JSONException e) {
                LOG.error("Failed to parse service response.", e);
            }
        }

        return ret;
    }

    private static String getCity(String address) {
        String tokens[] = address.split(",");
        String city = tokens[tokens.length - 1];
        
        return city.trim();
    }
}

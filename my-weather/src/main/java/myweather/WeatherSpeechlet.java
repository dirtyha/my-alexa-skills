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
import homecontrol.Responder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
    private final Responder responder = new Responder(CARD_TITLE);
    private static final String SESSION_ISWHATNEXT = "isWhatNext";
    // Get your own API key from https://ilmatieteenlaitos.fi/rekisteroityminen-avoimen-datan-kayttajaksi
    // and place it in Lambda environment variable
    private static final String APIKEY = System.getenv("api_key");
    private static final String URL_OBSERVATIONS = "http://data.fmi.fi/fmi-apikey/"
            + APIKEY
            + "/wfs?request=getFeature&storedquery_id=fmi::observations::weather::multipointcoverage";
    private static final String URL_FORECASTS = "http://data.fmi.fi/fmi-apikey/"
            + APIKEY
            + "/wfs?request=getFeature&storedquery_id=fmi::forecast::hirlam::surface::point::multipointcoverage";
    private static final String URL_GEOCODE = "http://maps.google.com/maps/api/geocode/xml?address=";
    private static final String SLOT_TIME = "time";
    private static final String SLOT_PLACE = "place";
    private static final String SLOT_PLACE_DEFAULT_VALUE = "home";
    private static final Map<String, Integer> TIMES = new HashMap<>();
    private static final Map<String, String> OBSERVATION_FIELDS = new LinkedHashMap<>();
    private static final Map<String, String> FORECAST_FIELDS = new LinkedHashMap<>();
    // default address is used only if no addresses exist in Google profile
    private static final String DEFAULT_ADDRESS = "Mannerheimintie 1, Helsinki";
    
    static {
        // key: forecast time in spoken text
        // value: forecast time in clock hours
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

        // put these values in the order you want to have them spoked by Alexa
        // key: field name in WFS responses
        // value: field name spoken by Alexa
        OBSERVATION_FIELDS.put("t2m", "temperature");
        OBSERVATION_FIELDS.put("wd_10min", "wind direction");
        OBSERVATION_FIELDS.put("ws_10min", "wind speed");
        OBSERVATION_FIELDS.put("r_1h", "rain fall");
        OBSERVATION_FIELDS.put("p_sea", "air pressure");
        OBSERVATION_FIELDS.put("rh", "relative humidity");
        OBSERVATION_FIELDS.put("td", "dew point temperature");
        OBSERVATION_FIELDS.put("n_man", "cloud amount");

        // put these values in the order you want to have them spoked by Alexa
        // key: field name in WFS responses
        // value: field name spoken by Alexa
        FORECAST_FIELDS.put("Temperature", "temperature");
        FORECAST_FIELDS.put("WindDirection", "wind direction");
        FORECAST_FIELDS.put("WindSpeedMS", "wind speed");
        FORECAST_FIELDS.put("Precipitation1h", "rain fall");
        FORECAST_FIELDS.put("Pressure", "air pressure");
        FORECAST_FIELDS.put("Humidity", "relative humidity");
        FORECAST_FIELDS.put("DewPoint", "dew point temperature");
        FORECAST_FIELDS.put("TotalCloudCover", "cloud amount");
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
        StringBuilder sb = new StringBuilder();

        sb.append("You can ask ");
        sb.append(CARD_TITLE);
        sb.append(" for weather observations or forecasts. ");
        sb.append("For example, you can ask: Alexa, ask ");
        sb.append(CARD_TITLE);
        sb.append(" observations for home. ");
        sb.append("Or: Alexa, ask ");
        sb.append(CARD_TITLE);
        sb.append(" forecast for work at tomorrow noon. ");
        sb.append("Allowed forecast times are: morning, noon, afternoon, evening, night, ");
        sb.append("tomorrow morning, tomorrow noon, tomorrow afternoon, tomorrow evening and tomorrow night.");

        return responder.askResponse(sb.toString());
    }

    private SpeechletResponse handleCurrentWeatherRequest(final Intent intent, final Session session) {
        StringBuilder sb = new StringBuilder();
        String place = SLOT_PLACE_DEFAULT_VALUE;
        if (intent.getSlot(SLOT_PLACE) != null && intent.getSlot(SLOT_PLACE).getValue() != null) {
            place = intent.getSlot(SLOT_PLACE).getValue();
        }

        String token = session.getUser().getAccessToken();
        if (token != null) {
            String address = getAddressFromGoogleProfile(token, place);
            if(address == null || address.isEmpty()) {
                address = DEFAULT_ADDRESS;
            }
            String city = getCity(address);

            if (city != null && !city.isEmpty()) {
                ZonedDateTime datetime = ZonedDateTime.now(ZoneId.of("Z")).minusHours(1).withMinute(0).withSecond(0).withNano(0);
                Map<String, String> observations = getObservations(city, datetime);

                if (observations.size() > 0) {
                    sb.append("Observations in ");
                    sb.append(city);
                    sb.append(" are: ");

                    boolean isFirst = true;
                    for (String key : observations.keySet()) {
                        if (!isFirst) {
                            sb.append(", ");
                        } else {
                            isFirst = false;
                        }
                        sb.append(key);
                        sb.append(" ");
                        sb.append(observations.get(key));
                    }
                    sb.append(".");
                } else {
                    sb.append("No observations found in ");
                    sb.append(city);
                    sb.append(".");
                }
            } else {
                sb.append("City is not set in the Google profile address.");
            }
        } else {
            // OAuth token is missing
            sb.append("Not authenticated by Google.");
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(sb.toString(), isWhatNext);
    }

    private SpeechletResponse handleForecastedWeatherRequest(final Intent intent, final Session session) {
        StringBuilder sb = new StringBuilder();

        String place = SLOT_PLACE_DEFAULT_VALUE;
        if (intent.getSlot(SLOT_PLACE) != null && intent.getSlot(SLOT_PLACE).getValue() != null) {
            place = intent.getSlot(SLOT_PLACE).getValue();
        }

        String time = null;
        if (intent.getSlot(SLOT_TIME) != null && intent.getSlot(SLOT_TIME).getValue() != null) {
            time = intent.getSlot(SLOT_TIME).getValue();
        }
        ZonedDateTime forecastTime = getForecastTime(time);

        String token = session.getUser().getAccessToken();
        if (token != null) {
            String address = getAddressFromGoogleProfile(token, place);
            if(address == null || address.isEmpty()) {
                address = DEFAULT_ADDRESS;
            }
            String location = getLocationFromAddress(address);
            String city = getCity(address);

            if (location != null && !location.isEmpty()) {
                Map<String, String> forecast = getForecast(location, forecastTime);

                if (forecast.size() > 0) {
                    sb.append("Forecast for ");
                    sb.append(city);
                    if (time != null) {
                        sb.append(" at ");
                        sb.append(time);
                    }
                    sb.append(" is: ");

                    boolean isFirst = true;
                    for (String key : forecast.keySet()) {
                        if (!isFirst) {
                            sb.append(", ");
                        } else {
                            isFirst = false;
                        }
                        sb.append(key);
                        sb.append(" ");
                        sb.append(forecast.get(key));
                    }
                    sb.append(".");
                } else {
                    sb.append("No forecast found for ");
                    sb.append(place);
                    sb.append(".");
                }
            } else {
                sb.append("Location not found for ");
                sb.append(place);
                sb.append(".");
            }
        } else {
            // OAuth token is missing
            sb.append("Not authenticated by Google.");
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(sb.toString(), isWhatNext);
    }

    private static Map<String, String> getObservations(final String city, final ZonedDateTime datetime) {
        Map<String, String> observations = new LinkedHashMap<>();

        String url = URL_OBSERVATIONS
                + "&place=" + city
                + "&starttime=" + datetime.format(DateTimeFormatter.ISO_DATE_TIME)
                + "&endtime=" + datetime.format(DateTimeFormatter.ISO_DATE_TIME);

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
            for (int i = 0; i < fields.getLength(); i++) {
                Element field = (Element) fields.item(i);
                String name = field.getAttribute("name");
                fieldNames.add(name);
            }

            for (String field : OBSERVATION_FIELDS.keySet()) {
                int i = fieldNames.indexOf(field);
                if (i > -1) {
                    observations.put(OBSERVATION_FIELDS.get(field), values[i]);
                }
            }
        } catch (ParserConfigurationException | DOMException | SAXException | IOException ex) {
            // reset builder to a blank string
            LOG.error("Failed to read observations.", ex);
        }

        return observations;
    }

    private static Map<String, String> getForecast(final String location, final ZonedDateTime forecastTime) {
        Map<String, String> forecast = new LinkedHashMap<>();

        String url = URL_FORECASTS
                + "&latlon=" + location
                + "&starttime=" + forecastTime.format(DateTimeFormatter.ISO_DATE_TIME)
                + "&endtime=" + forecastTime.format(DateTimeFormatter.ISO_DATE_TIME);

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
            for (int i = 0; i < fields.getLength(); i++) {
                Element field = (Element) fields.item(i);
                String name = field.getAttribute("name");
                fieldNames.add(name);
            }

            for (String field : FORECAST_FIELDS.keySet()) {
                int i = fieldNames.indexOf(field);
                if (i > -1) {
                    forecast.put(FORECAST_FIELDS.get(field), values[i]);
                }
            }
        } catch (ParserConfigurationException | DOMException | SAXException | IOException ex) {
            // reset builder to a blank string
            LOG.error("Failed to read observations.", ex);
        }

        return forecast;
    }

    private static String getAddressFromGoogleProfile(final String token, final String addressType) {
        String ret = null;
        StringBuffer response = null;
        try {
            URL obj = new URL("https://people.googleapis.com/v1/people/me?requestMask.includeField=person.names,person.addresses");
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
                    if (type.equals(addressType)) {
                        ret = text;
                        break;
                    }
                }
            } catch (JSONException ex) {
                LOG.error("Failed to parse service response.", ex);
            }
        }

        return ret;
    }

    private static String getCity(final String address) {
        String city = null;

        // expects that address format is <street>,<city>
        String tokens[] = address.split(",");
        if (tokens.length > 1) {
            city = tokens[tokens.length - 1].trim();
        }

        return city;
    }

    private static ZonedDateTime getForecastTime(final String strForecastTime) {
        LocalDateTime forecastTime = LocalDateTime.now();
        int addDays = 0;
        int hour = 0;
        if (strForecastTime == null) {
            // forecast time not specified, use next available 
            // forecast time counting from current time
            Iterator<Integer> iter = TIMES.values().iterator();
            while (iter.hasNext()) {
                hour = iter.next();
                if (hour > forecastTime.getHour()) {
                    if (hour > 24) {
                        hour -= 24;
                        addDays += 1;
                    }
                    break;
                }
            }
        } else {
            hour = TIMES.get(strForecastTime);
            if (hour > 24) {
                hour -= 24;
                addDays += 1;
            }
        }

        forecastTime = forecastTime.withHour(hour)
                .plusDays(addDays)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        return ZonedDateTime.of(forecastTime, ZoneId.of("Z"));
    }

    private static String getLocationFromAddress(String address) {
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

}

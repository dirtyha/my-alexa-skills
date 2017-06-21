package airquality;

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
import com.amazon.speech.ui.SimpleCard;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import handler.PlacesHandler;
import homecontrol.Responder;
import java.io.IOException;
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
import location.Google;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class AQSpeechlet implements Speechlet {

    private static final Logger LOG = LoggerFactory.getLogger(AQSpeechlet.class);

    private static final String CARD_TITLE = "Air Quality";
    private static final String SESSION_ISWHATNEXT = "isWhatNext";
    // Get your own API key from https://ilmatieteenlaitos.fi/rekisteroityminen-avoimen-datan-kayttajaksi
    // and place it in Lambda environment variable
    private static final String APIKEY = System.getenv("api_key");
    private static final String URL_OBSERVATIONS = "http://data.fmi.fi/fmi-apikey/"
            + APIKEY
            + "/wfs?request=getFeature&storedquery_id=urban::observations::airquality::hourly::multipointcoverage";
    private static final String URL_FORECASTS = "http://data.fmi.fi/fmi-apikey/"
            + APIKEY
            + "/wfs?request=getFeature&storedquery_id=fmi::forecast::silam::airquality::surface::point::multipointcoverage";
    private static final String SLOT_TIME = "time";
    private static final String SLOT_PLACE = "place";
    private static final String SLOT_PLACE_DEFAULT_VALUE = "home";
    private static final Map<String, Integer> TIMES = new HashMap<>();
    private static final Map<String, String> OBSERVATION_FIELDS = new LinkedHashMap<>();
    private static final Map<String, String> FORECAST_FIELDS = new LinkedHashMap<>();
    private static final String AQ_INDEX = "air quality index";
    // default address is used only if no saved places exist
    private static final String DEFAULT_ADDRESS = "Mannerheimintie 1, Helsinki";
    private static final String[] AQ_INDICES = new String[] {
        "good", 
        "satisfactory",
        "fair",
        "poor",
        "very poor"
    };
    private AmazonDynamoDBClient dbClient;
    private Responder responder;
    private PlacesHandler placesHandler;
    
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
        OBSERVATION_FIELDS.put("AQINDEX_PT1H_avg", AQ_INDEX);
        OBSERVATION_FIELDS.put("PM10_PT1H_avg", "particles < 10 µm");
        OBSERVATION_FIELDS.put("PM25_PT1H_avg", "particles < 2.5 µm");
        OBSERVATION_FIELDS.put("SO2_PT1H_avg", "sulphur dioxide");
        OBSERVATION_FIELDS.put("TRSC_PT1H_avg", "odorous sulphur compounds");
        OBSERVATION_FIELDS.put("NO_PT1H_avg", "nitrogen monoxide");
        OBSERVATION_FIELDS.put("NO2_PT1H_avg", "nitrogen dioxide");
        OBSERVATION_FIELDS.put("O3_PT1H_avg", "ozone");
        OBSERVATION_FIELDS.put("CO_PT1H_avg", "carbon monoxide");

        // put these values in the order you want to have them spoked by Alexa
        // key: field name in WFS responses
        // value: field name spoken by Alexa
        FORECAST_FIELDS.put("PM10Concentration", "particles < 10 µm");
        FORECAST_FIELDS.put("PM25Concentration", "particles < 2.5 µm");
        FORECAST_FIELDS.put("SO2Concentration", "sulphur dioxide");
        FORECAST_FIELDS.put("COConcentration", "carbon monoxide");
        FORECAST_FIELDS.put("NOConcentration", "nitrogen monoxide");
        FORECAST_FIELDS.put("NO2Concentration", "nitrogen dioxide");
        FORECAST_FIELDS.put("O3Concentration", "ozone");
    }

    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        LOG.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        if (dbClient == null) {
            dbClient = new AmazonDynamoDBClient();
            dbClient.setRegion(Region.getRegion(Regions.EU_WEST_1));
        }
        responder = new Responder(CARD_TITLE);
        placesHandler = new PlacesHandler(CARD_TITLE, dbClient);

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

        if ("CurrentAirQualityIntent".equals(intentName)) {
            return handleCurrentAirQualityRequest(intent, session);
        } else if ("ForecastedAirQualityIntent".equals(intentName)) {
            return handleForecastedAirQualityRequest(intent, session);
        } else if ("GetIndexIntent".equals(intentName)) {
            return handleGetIndexRequest(intent, session);
        } else if ("GetPlacesIntent".equals(intentName)) {
            return placesHandler.handleGetPlacesRequest(intent, session);
        } else if ("SavePlacesIntent".equals(intentName)) {
            return placesHandler.handleSavePlacesRequest(intent, session);
        } else if ("AMAZON.HelpIntent".equals(intentName)) {
            return handleHelpRequest(session);
        } else if ("AMAZON.StopIntent".equals(intentName)) {
            return responder.tellResponse("Goodbye");
        } else if ("AMAZON.CancelIntent".equals(intentName)) {
            return responder.tellResponse("Goodbye");
        } else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    private SpeechletResponse getWelcomeResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append("Welcome to ");
        sb.append(CARD_TITLE);
        sb.append(". ");
        sb.append(getBriefHelpText());

        return responder.askResponse(sb.toString(), getFullHelpText());
    }

    private String getBriefHelpText() {
        StringBuilder sb = new StringBuilder();

        sb.append("You can ask ");
        sb.append(CARD_TITLE);
        sb.append(" to get air quality observations or forecasts for the saved places. ");
        sb.append("For example, you can say: Alexa, ask ");
        sb.append(CARD_TITLE);
        sb.append(" to get observations for home. ");

        return sb.toString();
    }

    private String getFullHelpText() {
        StringBuilder sb = new StringBuilder();

        sb.append("You can add places in your to-do list and ask ");
        sb.append(CARD_TITLE);
        sb.append(" to persist them. ");
        sb.append("For example, you can say: Alexa, ask ");
        sb.append(CARD_TITLE);
        sb.append(" to save places. ");
        sb.append(getBriefHelpText());
        sb.append("Or: Alexa, ask ");
        sb.append(CARD_TITLE);
        sb.append(" to get forecast for work at tomorrow noon. ");
        sb.append("Allowed forecast times are: morning, noon, afternoon, evening and night. ");
        sb.append("Check the skill card in Alexa application and follow the link for more documentation about this skill.");

        return sb.toString();
    }

    private SpeechletResponse handleHelpRequest(Session session) {
        SimpleCard card = new SimpleCard();
        card.setTitle(CARD_TITLE);
        card.setContent("For help, check the quick start guide in https://alexapublic.s3.amazonaws.com/air-quality.html");

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(getFullHelpText(), isWhatNext, card);
    }

    private SpeechletResponse handleGetIndexRequest(final Intent intent, final Session session) {
        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        StringBuilder sb = new StringBuilder();
        String placeName = SLOT_PLACE_DEFAULT_VALUE;
        if (intent.getSlot(SLOT_PLACE) != null && intent.getSlot(SLOT_PLACE).getValue() != null) {
            placeName = intent.getSlot(SLOT_PLACE).getValue().replace(" ", "");
        }

        String userId = session.getUser().getUserId();
        String address = placesHandler.getAddressForPlace(userId, placeName);
        if (address == null) {
            sb.append("Your place ");
            sb.append(placeName);
            sb.append(" has not been added yet. ");
            sb.append("Using default place. ");
            address = DEFAULT_ADDRESS;
        }
        String city = Google.getCity(address);
        
        if (city != null && !city.isEmpty()) {
            ZonedDateTime datetime = ZonedDateTime.now(ZoneId.of("Z")).minusHours(1).withMinute(0).withSecond(0).withNano(0);
            Map<String, String> observations = getObservations(city, datetime);

            if (observations.size() > 0 && observations.containsKey(AQ_INDEX)) {
                sb.append("Air quality index for ");
                sb.append(city);
                sb.append(" is: ");
                String value = observations.get(AQ_INDEX);
                try {
                    int index = (int)Double.parseDouble(value); 
                    sb.append(AQ_INDICES[index - 1]);
                } catch(NumberFormatException ex) {
                    sb.append(value);
                }
                sb.append(".");            
            } else {
                sb.append("No observations found for ");
                sb.append(city);
                sb.append(".");
                isWhatNext = false;
            }
        } else {
            sb.append("City is not set in the place address.");
            isWhatNext = false;
        }

        return responder.respond(sb.toString(), isWhatNext);
    }

    private SpeechletResponse handleCurrentAirQualityRequest(final Intent intent, final Session session) {
        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        StringBuilder sb = new StringBuilder();
        String placeName = SLOT_PLACE_DEFAULT_VALUE;
        if (intent.getSlot(SLOT_PLACE) != null && intent.getSlot(SLOT_PLACE).getValue() != null) {
            placeName = intent.getSlot(SLOT_PLACE).getValue().replace(" ", "");
        }

        String userId = session.getUser().getUserId();
        String address = placesHandler.getAddressForPlace(userId, placeName);
        if (address == null) {
            sb.append("Your place ");
            sb.append(placeName);
            sb.append(" has not been added yet. ");
            sb.append("Using default place. ");
            address = DEFAULT_ADDRESS;
        }
        String city = Google.getCity(address);
        
        if (city != null && !city.isEmpty()) {
            ZonedDateTime datetime = ZonedDateTime.now(ZoneId.of("Z")).minusHours(1).withMinute(0).withSecond(0).withNano(0);
            Map<String, String> observations = getObservations(city, datetime);

            if (observations.size() > 0) {
                sb.append("Observations for ");
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
                sb.append("No observations found for ");
                sb.append(city);
                sb.append(".");
                isWhatNext = false;
            }
        } else {
            sb.append("City is not set in the place address.");
            isWhatNext = false;
        }

        return responder.respond(sb.toString(), isWhatNext);
    }

    private SpeechletResponse handleForecastedAirQualityRequest(final Intent intent, final Session session) {
        StringBuilder sb = new StringBuilder();
        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);

        String placeName = SLOT_PLACE_DEFAULT_VALUE;
        if (intent.getSlot(SLOT_PLACE) != null && intent.getSlot(SLOT_PLACE).getValue() != null) {
            placeName = intent.getSlot(SLOT_PLACE).getValue().replace(" ", "");
        }

        String time = null;
        if (intent.getSlot(SLOT_TIME) != null && intent.getSlot(SLOT_TIME).getValue() != null) {
            time = intent.getSlot(SLOT_TIME).getValue();
        }
        ZonedDateTime forecastTime = getForecastTime(time);

        String userId = session.getUser().getUserId();
        String address = placesHandler.getAddressForPlace(userId, placeName);
        if (address == null) {
            sb.append("Your place ");
            sb.append(placeName);
            sb.append(" has not been added yet. ");
            sb.append("Using default place. ");
            address = DEFAULT_ADDRESS;
        }
        String location = Google.getLocationFromAddress(address);
        String city = Google.getCity(address);

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
                sb.append(placeName);
                sb.append(".");
                isWhatNext = false;
            }
        } else {
            sb.append("Location not found for ");
            sb.append(placeName);
            sb.append(".");
            isWhatNext = false;
        }

        return responder.respond(sb.toString(), isWhatNext);
    }

    private static Map<String, String> getObservations(final String city, final ZonedDateTime datetime) {
        Map<String, String> observations = new LinkedHashMap<>();

        // A hack. WFS API has problems in finding data for Vantaa
        String myCity = city;
        if(city.toLowerCase().equals("vantaa")) {
            myCity = "tikkurila,vantaa";
        }

        String url = URL_OBSERVATIONS
                + "&place=" + myCity
                + "&starttime=" + datetime.format(DateTimeFormatter.ISO_DATE_TIME)
                + "&endtime=" + datetime.format(DateTimeFormatter.ISO_DATE_TIME);

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(url);
            doc.getDocumentElement().normalize();

            Element list = (Element) doc.getElementsByTagName("gml:doubleOrNilReasonTupleList").item(0);
            if (list != null) {
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
                    if (i > -1 && !values[i].equals("NaN")) {
                        observations.put(OBSERVATION_FIELDS.get(field), values[i]);
                    }
                }
            }
        } catch (ParserConfigurationException | DOMException | SAXException | IOException ex) {
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
            LOG.error("Failed to read observations.", ex);
        }

        return forecast;
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

}

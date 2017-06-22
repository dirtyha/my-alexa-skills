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
import com.amazon.speech.ui.SimpleCard;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import handler.PlacesHandler;
import homecontrol.Responder;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import location.Google;
import measurement.Parameter;
import measurement.Unit;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import time.TimeUtil;

public class WeatherSpeechlet implements Speechlet {

    private static class FieldDescription {

        private final String displayName;
        private final Unit unit;

        private FieldDescription(String displayName, Unit unit) {
            this.displayName = displayName;
            this.unit = unit;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(WeatherSpeechlet.class);

    private static final String CARD_TITLE = "My Weather";
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
    private static final String SLOT_TIME = "time";
    private static final String SLOT_PLACE = "place";
    private static final String SLOT_PLACE_DEFAULT_VALUE = "home";
    private static final Map<String, FieldDescription> OBSERVATION_FIELDS = new LinkedHashMap<>();
    private static final Map<String, FieldDescription> FORECAST_FIELDS = new LinkedHashMap<>();
    // default address is used only if no saved places exist 
    private static final String DEFAULT_ADDRESS = "Mannerheimintie 1, Helsinki";
    private AmazonDynamoDBClient dbClient;
    private Responder responder;
    private PlacesHandler placesHandler;

    static {

        // put these values in the order you want to have them spoked by Alexa
        // key: field name in WFS responses
        // value: field name spoken by Alexa
        OBSERVATION_FIELDS.put("t2m", new FieldDescription("temperature", Unit.DEGREES));
        OBSERVATION_FIELDS.put("wd_10min", new FieldDescription("wind direction", Unit.WINDDIR));
        OBSERVATION_FIELDS.put("ws_10min", new FieldDescription("wind speed", Unit.MPS));
        OBSERVATION_FIELDS.put("r_1h", new FieldDescription("rain fall", Unit.MMPH));
        OBSERVATION_FIELDS.put("p_sea", new FieldDescription("air pressure", Unit.HPA));
        OBSERVATION_FIELDS.put("rh", new FieldDescription("relative humidity", Unit.PERCENT));
        OBSERVATION_FIELDS.put("td", new FieldDescription("dew point temperature", Unit.DEGREES));
        OBSERVATION_FIELDS.put("n_man", new FieldDescription("cloud coverage", Unit.CLOUDCOVERAGE));

        // put these values in the order you want to have them spoked by Alexa
        // key: field name in WFS responses
        // value: field name spoken by Alexa
        FORECAST_FIELDS.put("Temperature", new FieldDescription("temperature", Unit.DEGREES));
        FORECAST_FIELDS.put("WindDirection", new FieldDescription("wind direction", Unit.WINDDIR));
        FORECAST_FIELDS.put("WindSpeedMS", new FieldDescription("wind speed", Unit.MPS));
        FORECAST_FIELDS.put("Precipitation1h", new FieldDescription("rain fall", Unit.MMPH));
        FORECAST_FIELDS.put("Pressure", new FieldDescription("air pressure", Unit.HPA));
        FORECAST_FIELDS.put("Humidity", new FieldDescription("relative humidity", Unit.PERCENT));
        FORECAST_FIELDS.put("DewPoint", new FieldDescription("dew point temperature", Unit.DEGREES));
        FORECAST_FIELDS.put("TotalCloudCover", new FieldDescription("cloud coverage", Unit.PERCENT));
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

        if ("CurrentWeatherIntent".equals(intentName)) {
            return handleCurrentWeatherRequest(intent, session);
        } else if ("ForecastedWeatherIntent".equals(intentName)) {
            return handleForecastedWeatherRequest(intent, session);
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
        sb.append(" to get weather observations or forecasts for the saved places. ");
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
        card.setContent("For help, check the quick start guide in https://alexapublic.s3.amazonaws.com/my-weather.html");

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(getFullHelpText(), isWhatNext, card);
    }

    private SpeechletResponse handleCurrentWeatherRequest(final Intent intent, final Session session) {
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
            List<Parameter> observations = getObservations(city, datetime);

            if (observations.size() > 0) {
                sb.append("Observations for ");
                sb.append(city);
                sb.append(" are: ");

                boolean isFirst = true;
                for (Parameter observation : observations) {
                    if (!isFirst) {
                        sb.append(", ");
                    } else {
                        isFirst = false;
                    }
                    sb.append(observation);
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

    private SpeechletResponse handleForecastedWeatherRequest(final Intent intent, final Session session) {
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
        ZonedDateTime forecastTime = TimeUtil.getForecastTime(time);

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
            List<Parameter> forecast = getForecast(location, forecastTime);

            if (forecast.size() > 0) {
                sb.append("Forecast for ");
                sb.append(city);
                if (time != null) {
                    sb.append(" at ");
                    sb.append(time);
                }
                sb.append(" is: ");

                boolean isFirst = true;
                for (Parameter p : forecast) {
                    if (!isFirst) {
                        sb.append(", ");
                    } else {
                        isFirst = false;
                    }
                    sb.append(p);
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
            sb.append(" The address may be incorrect.");
            isWhatNext = false;
        }

        return responder.respond(sb.toString(), isWhatNext);
    }

    private static List<Parameter> getObservations(final String city, final ZonedDateTime datetime) {
        List<Parameter> observations = new LinkedList<>();

        // A hack. WFS API has problems in finding data for Vantaa
        // for some reason this works.
        String myCity = city;
        if (city.toLowerCase().equals("vantaa")) {
            myCity = "tikkurila,vantaa";
        }

        String url = URL_OBSERVATIONS
                + "&place=" + myCity
                + "&starttime=" + datetime.format(DateTimeFormatter.ISO_DATE_TIME)
                + "&endtime=" + datetime.format(DateTimeFormatter.ISO_DATE_TIME);
        LOG.info("Observation URL: " + url);

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
                        FieldDescription desc = OBSERVATION_FIELDS.get(field);
                        Parameter p = new Parameter(desc.displayName, values[i], desc.unit);
                        observations.add(p);
                    }
                }
            }
        } catch (ParserConfigurationException | DOMException | SAXException | IOException ex) {
            LOG.error("Failed to read observations.", ex);
        }

        return observations;
    }

    private static List<Parameter> getForecast(final String location, final ZonedDateTime forecastTime) {
        List<Parameter> forecast = new LinkedList<>();

        String url = URL_FORECASTS
                + "&latlon=" + location
                + "&starttime=" + forecastTime.format(DateTimeFormatter.ISO_DATE_TIME)
                + "&endtime=" + forecastTime.format(DateTimeFormatter.ISO_DATE_TIME);
        LOG.info("Forecast URL: " + url);
        
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
                    FieldDescription desc = FORECAST_FIELDS.get(field);
                    Parameter p = new Parameter(desc.displayName, values[i], desc.unit);
                    forecast.add(p);
                }
            }
        } catch (ParserConfigurationException | DOMException | SAXException | IOException ex) {
            LOG.error("Failed to read observations.", ex);
        }

        return forecast;
    }

}

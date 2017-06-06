package myweather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import homecontrol.HttpRequest;
import homecontrol.Responder;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
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
    private static final String URL_OBSERVATIONS = "http://data.fmi.fi/fmi-apikey/" + APIKEY + 
            "/wfs?request=getFeature&storedquery_id=fmi::observations::weather::multipointcoverage"; // &place=espoo&starttime=2017-06-06T13:00:00Z&endtime=2017-06-06T13:00:00Z";
    private static final String URL_FORECASTS = "http://data.fmi.fi/fmi-apikey/" + APIKEY + 
            "/wfs?request=getFeature&storedquery_id=fmi::forecast::hirlam::surface::point::multipointcoverage"; // &latlon=60.18553,24.61650&starttime=2017-06-06T13:00:00Z&endtime=2017-06-06T15:00:00Z";

    
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

    private SpeechletResponse handleCurrentWetherRequest(final Intent intent, final Session session) {
        StringBuilder sb = new StringBuilder();
        sb.append("Observations are: ");

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(sb.toString(), isWhatNext);
    }

    private SpeechletResponse handleForecastedWeatherRequest(final Intent intent, final Session session) {
        StringBuilder sb = new StringBuilder();
        sb.append("Forecast is: ");

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(sb.toString(), isWhatNext);
    }

    private Map<String, String> getData(String date, String area) {
        Map<String, String> movies = new HashMap<>();

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(URL + "dt=" + date + "&area=" + area);
            doc.getDocumentElement().normalize();

            Element showList = (Element) doc.getElementsByTagName("Shows").item(0);
            NodeList shows = showList.getElementsByTagName("Show");
            for (int idxShow = 0; idxShow < shows.getLength(); idxShow++) {
                Element show = (Element) shows.item(idxShow);
                String title = show.getElementsByTagName("OriginalTitle").item(0).getTextContent();
                String time = show.getElementsByTagName("dttmShowStart").item(0).getTextContent();
                if (title != null && time != null) {
                    title = trimTitle(title);
                    LocalDateTime showTime = LocalDateTime.parse(time);
                    String language = show.getElementsByTagName("PresentationMethodAndLanguage").item(0).getTextContent();
                    if (language == null) {
                        language = "";
                    } else {
                        language = language.toLowerCase();
                    }

                    if (!language.contains("suomi")
                            && !language.contains("ruotsi")
                            && !movies.keySet().contains(title)
                            && !title.contains("(dub)")) {
                        movies.put(title, showTime.format(DateTimeFormatter.ISO_LOCAL_TIME));
                    }
                }
            }
        } catch (ParserConfigurationException | DOMException | SAXException | IOException e) {
            // reset builder to a blank string
            System.out.println("Failed to read RSS feed.");
        }

        return movies;
    }

}

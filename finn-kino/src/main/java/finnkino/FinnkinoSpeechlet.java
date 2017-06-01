package finnkino;

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

public class FinnkinoSpeechlet implements Speechlet {

    private static final Logger LOG = LoggerFactory.getLogger(FinnkinoSpeechlet.class);

    private static final String CARD_TITLE = "Finnkino";
    private final HttpRequest wsRequest = new HttpRequest();
    private final Responder responder = new Responder(CARD_TITLE);
    private static final String SESSION_ISWHATNEXT = "isWhatNext";
    private static final String SLOT_CINEMA = "cinema";
    private static final String SLOT_DATE = "date";
    private static final String SLOT_MOVIE = "movie";
    private static final String DEFAULT_CINEMA_NAME = "Espoo big apple";
    private static final String DEFAULT_CINEMA_ID = "1039";
    private static final String URL = "http://www.finnkino.fi/xml/Schedule?";
    private static final HashMap<String, String> CINEMAS = new HashMap<>();

    static {
        CINEMAS.put(DEFAULT_CINEMA_NAME, DEFAULT_CINEMA_ID);
        CINEMAS.put("Espoo cello", "1038");
        CINEMAS.put("Helsinki tennis palace", "1033");
        CINEMAS.put("Helsinki kino palace", "1031");
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

        if ("ListMoviesIntent".equals(intentName)) {
            return handleListMoviesRequest(intent, session);
        } else if ("ListCinemasIntent".equals(intentName)) {
            return handleListCinemasRequest(intent, session);
        } else if ("QueryMovieIntent".equals(intentName)) {
            return handleQueryMovieRequest(intent, session);
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

    private SpeechletResponse handleListCinemasRequest(final Intent intent, final Session session) {
        StringBuilder sb = new StringBuilder();
        sb.append("Cinemas are: ");

        boolean isFirst = true;
        for (String cinema : CINEMAS.keySet()) {
            if (!isFirst) {
                sb.append(", ");
            } else {
                isFirst = false;
            }
            sb.append(cinema);
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(sb.toString(), isWhatNext);
    }

    private SpeechletResponse handleListMoviesRequest(final Intent intent, final Session session) {
        String speechText;

        String cinemaId = DEFAULT_CINEMA_ID;
        String cinemaName = DEFAULT_CINEMA_NAME;
        Slot cinemaSlot = intent.getSlot(SLOT_CINEMA);
        if (cinemaSlot != null && cinemaSlot.getValue() != null) {
            cinemaName = cinemaSlot.getValue();
            cinemaId = CINEMAS.get(cinemaName);
        }

        LocalDate date;
        Slot dateSlot = intent.getSlot(SLOT_DATE);
        if (dateSlot != null && dateSlot.getValue() != null) {
            date = LocalDate.parse(dateSlot.getValue());
        } else {
            date = LocalDate.now();
        }
        DateTimeFormatter apiFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String apiDay = date.format(apiFormatter);
        DateTimeFormatter alexaFormatter = DateTimeFormatter.ofPattern("d MMM");
        String alexaDay = date.format(alexaFormatter);

        Map<String, String> movies = getMovies(apiDay, cinemaId);
        if (movies.isEmpty()) {
            speechText = "No movies today.";
        } else {
            speechText = alexaDay + " in " + cinemaName + " are playing: ";
            Iterator<String> iter = movies.keySet().iterator();
            boolean isFirst = true;
            while(iter.hasNext()) {
                if (!isFirst) {
                    speechText += ", ";
                } else {
                    isFirst = false;
                }
                speechText += iter.next();
            }
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(speechText, isWhatNext);
    }

    private SpeechletResponse handleQueryMovieRequest(final Intent intent, final Session session) {
        String speechText;

        LocalDate date = LocalDate.now();
        DateTimeFormatter apiFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String apiDay = date.format(apiFormatter);

        String cinemaId = DEFAULT_CINEMA_ID;
        Slot cinemaSlot = intent.getSlot(SLOT_CINEMA);
        if (cinemaSlot != null && cinemaSlot.getValue() != null) {
            String cinemaName = cinemaSlot.getValue();
            cinemaId = CINEMAS.get(cinemaName);
        }

        String movie = null;
        Slot movieSlot = intent.getSlot(SLOT_MOVIE);
        if (movieSlot != null && movieSlot.getValue() != null) {
            movie = movieSlot.getValue();
        }

        if (movie == null) {
            speechText = "No movie specified.";
        } else {
            Map<String, String> movies = getMovies(apiDay, cinemaId);
            if (movies.isEmpty()) {
                speechText = "No movies today.";
            } else {
                if(movies.containsKey(movie)) {
                    speechText = movie + " plays at " + movies.get(movie);
                } else {
                    speechText = movie + " does not play today.";
                }
            }
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(speechText, isWhatNext);
    }

    private Map<String, String> getMovies(String date, String area) {
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

    private static String trimTitle(String title) {
        String ret = title.replaceAll("\\(([^)]+)\\)", "");
        return ret.replaceAll("2D|3D", "");
    }

}

package myearth;

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
import homecontrol.Responder;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class EarthSpeechlet implements Speechlet {

    private static final Logger LOG = LoggerFactory.getLogger(EarthSpeechlet.class);

    private static final String CARD_TITLE = "My Earth";
    private final Responder responder = new Responder(CARD_TITLE);
    private static final String SESSION_ISWHATNEXT = "isWhatNext";
    private static final String URL = "https://www.esrl.noaa.gov/gmd/webdata/ccgg/trends/rss.xml";

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

        if ("GetCOtwoIntent".equals(intentName)) {
            return handleGetCO2Request(intent, session);
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
        sb.append(getHelpText());

        return responder.askResponse(sb.toString(), getHelpText());
    }

    private SpeechletResponse handleHelpRequest(Session session) {
        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(getHelpText(), isWhatNext);
    }

    private String getHelpText() {
        StringBuilder sb = new StringBuilder();
        sb.append("You can ask ");
        sb.append(CARD_TITLE);
        sb.append(" to get weekly earth atmosphere carbon dioxide amount. ");
        sb.append("For example, you can say: ");
        sb.append("Alexa, ask ");
        sb.append(CARD_TITLE);
        sb.append(" CO2.");

        return sb.toString();
    }

    private SpeechletResponse handleGetCO2Request(final Intent intent, final Session session) {
        return responder.respond(getCO2(), false);
    }

    private String getCO2() {
        String description = null;
        
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(URL);
            doc.getDocumentElement().normalize();

            NodeList itemList = doc.getElementsByTagName("item");
            Element item = (Element) itemList.item(2);
            description = item.getElementsByTagName("description").item(0).getTextContent();
            if(description != null) {
                description = description.replaceAll("<br>", ".");
                description = description.replaceAll("\n      ", "");
                description = description.replaceAll("\\n    ", "");
                description = description.replaceAll(" \\.", ". ");
            }
        } catch (ParserConfigurationException | DOMException | SAXException | IOException e) {
            // reset builder to a blank string
            System.out.println("Failed to read RSS feed.");
        }

        return description;
    }

}

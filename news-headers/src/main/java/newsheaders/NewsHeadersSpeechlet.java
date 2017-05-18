package newsheaders;

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
import homecontrol.HttpRequest;
import homecontrol.Responder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class NewsHeadersSpeechlet implements Speechlet {

    private static final Logger LOG = LoggerFactory.getLogger(NewsHeadersSpeechlet.class);

    private static final String CARD_TITLE = "News Headers";
    private final HttpRequest wsRequest = new HttpRequest();
    private final Responder responder = new Responder(CARD_TITLE);
    private static final String SESSION_ISWHATNEXT = "isWhatNext";
    private static final String URL = "http://feeds.yle.fi/uutiset/v1/recent.rss?publisherIds=YLE_NEWS";

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
        return handleStartRequest(session);
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        LOG.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        Intent intent = request.getIntent();
        String intentName = (intent != null) ? intent.getName() : null;

        if ("AMAZON.HelpIntent".equals(intentName)) {
            return handleHelpRequest();
        } else if ("AMAZON.StopIntent".equals(intentName)) {
            return responder.tellResponse("Goodbye");
        } else if ("AMAZON.CancelIntent".equals(intentName)) {
            return responder.tellResponse("Goodbye");
        } else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    private SpeechletResponse handleHelpRequest() {
        String speechText = "You can ask news headers.";

        return responder.askResponse(speechText);
    }

    private SpeechletResponse handleStartRequest(final Session session) {
        StringBuilder sb = new StringBuilder();

        List<String> headers = getHeaders();
        if (headers.isEmpty()) {
            sb.append("No news today.");
        } else {
            int max = headers.size() > 1 ? 1 : headers.size();
            for (int i = 0; i < max; i++) {
                if (i != 0) {
                    sb.append(" ");
                }
                sb.append(headers.get(i));
            }
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(sb.toString(), isWhatNext);
    }

    private List<String> getHeaders() {
        List<String> headers = new ArrayList<>();

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(URL);
            doc.getDocumentElement().normalize();

            NodeList channels = doc.getElementsByTagName("channel");
            Element channel = (Element) channels.item(0);
            NodeList items = channel.getElementsByTagName("item");
            for (int idxItem = 0; idxItem < items.getLength(); idxItem++) {
                Element item = (Element) items.item(idxItem);
                String title = item.getElementsByTagName("title").item(0).getTextContent();
                String description = item.getElementsByTagName("description").item(0).getTextContent();
                headers.add(title + ". " + description);
            }
        } catch (IOException | ParserConfigurationException | DOMException | SAXException e) {
            // reset builder to a blank string
            System.out.println("Failed to read RSS feed.");
        }

        return headers;
    }
}

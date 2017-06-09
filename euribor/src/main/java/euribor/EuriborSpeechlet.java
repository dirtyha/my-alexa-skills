package euribor;

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
import java.util.LinkedHashMap;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class EuriborSpeechlet implements Speechlet {

    private static final Logger LOG = LoggerFactory.getLogger(EuriborSpeechlet.class);

    private static final String CARD_TITLE = "Bank Tracker";
    private static final String SKILL_NAME = "Bank Tracker";
    private final Responder responder = new Responder(CARD_TITLE);
    private static final String SESSION_ISWHATNEXT = "isWhatNext";
    private static final String URL = "http://www.euribor-rates.eu/";

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

        if ("GetEuriborIntent".equals(intentName)) {
            return handleGetEuriborRequest(intent, session);
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
        return responder.askResponse("Ready", getHepText());
    }

    private SpeechletResponse handleHelpRequest() {
        return responder.askResponse(getHepText());
    }

    private static String getHepText() {
        return "You can ask " + SKILL_NAME + " about yesterday's interest rates. "
                + "For example, you can say: Alexa, ask " + SKILL_NAME + " to get rates.";
    }

    private SpeechletResponse handleGetEuriborRequest(final Intent intent, final Session session) {
        StringBuilder sb = new StringBuilder();
        sb.append("Yesterday's european interbank offered rates were: ");

        Map<String, String> rates = getRates();
        boolean isFirst = true;
        for (String key : rates.keySet()) {
            if (!isFirst) {
                sb.append(", ");
            } else {
                isFirst = false;
            }
            sb.append(key);
            sb.append(" ");
            sb.append(rates.get(key));
        }
        sb.append(".");

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(sb.toString(), isWhatNext);
    }

    private static Map<String, String> getRates() {
        Map<String, String> rates = new LinkedHashMap<>();

        try {
            Document doc = Jsoup.connect(URL).get();
            Elements tables = doc.getElementsByTag("TABLE");
            Element myTable = tables.get(8);
            Elements tds = myTable.getElementsByTag("TD");

            String m1 = tds.get(5).text();
            rates.put("1 month", m1);

            String m3 = tds.get(9).text();
            rates.put("3 month", m3);

            String m6 = tds.get(13).text();
            rates.put("6 month", m6);

            String m12 = tds.get(17).text();
            rates.put("12 month", m12);

        } catch (IOException e) {
            // reset builder to a blank string
            LOG.error("Failed to read euribor rates.", e);
        }

        return rates;
    }

}

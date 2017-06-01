package pizzaboy;

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
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.amazonaws.util.json.JSONTokener;
import homecontrol.Responder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.io.IOUtils;

public class PizzaboySpeechlet implements Speechlet {

    private static final Logger LOG = LoggerFactory.getLogger(PizzaboySpeechlet.class);

    private static final String CARD_TITLE = "Pizzaboy";
    private final Responder responder = new Responder(CARD_TITLE);
    private static final String SESSION_ISWHATNEXT = "isWhatNext";

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

        if ("GetAddressIntent".equals(intentName)) {
            return handleGetAddressRequest(session);
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

    private SpeechletResponse handleGetAddressRequest(final Session session) {
        String speechText = "Your address is ";
        String token = session.getUser().getAccessToken();

        Map<String, String> response = sendGet("https://people.googleapis.com/v1/people/me", token);
        for (String key : response.keySet()) {
            speechText += key + " ";
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(speechText, isWhatNext);
    }

    private SpeechletResponse getWelcomeResponse() {
        return responder.askResponse("Ready");
    }

    private SpeechletResponse handleHelpRequest() {
        String speechText = "You can ask " + CARD_TITLE + " to order pizza.";

        return responder.askResponse(speechText);
    }

    private Map<String, String> sendGet(String url, String token) {
        Map<String, String> ret = new HashMap<>();

        StringBuffer response = null;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // optional default is GET
            con.setRequestMethod("GET");

            //add request header
            con.setRequestProperty("Authorization", "Bearer " + token);

            int responseCode = con.getResponseCode();
            LOG.info("\nSending 'GET' request to URL : " + url);
            LOG.info("Response Code : " + responseCode);

            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                response = new StringBuffer();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }
        } catch (IOException ex) {
            LOG.error("Failed to send GET request: ", ex);
        }

        if (response != null && response.length() != 0) {
            try {
                JSONObject responseObject = new JSONObject(new JSONTokener(response.toString()));
                Iterator<String> iterator = responseObject.sortedKeys();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    String value = responseObject.getString(key);
                    ret.put(key, value);
                }
            } catch (JSONException e) {
                LOG.error("Failed to parse service response.", e);
            }
        }

        return ret;
    }

    public Map<String, String> get(String path, Map<String, String> parameters) {
        Map<String, String> response = null;

//        String queryString = query(parameters);
        InputStreamReader inputStream = null;
        BufferedReader bufferedReader = null;
        StringBuilder builder = new StringBuilder();
        try {
            String line;
            URL url = new URL(path);
            inputStream = new InputStreamReader(url.openStream());
            bufferedReader = new BufferedReader(inputStream);
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }
        } catch (Exception e) {
            // reset builder to a blank string
            LOG.error("Failed to call web service.", e);
            builder.setLength(0);
        } finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(bufferedReader);
        }

        if (builder.length() != 0) {
            response = new HashMap<>();
            try {
                JSONObject responseObject = new JSONObject(new JSONTokener(builder.toString()));
                Iterator<String> iterator = responseObject.sortedKeys();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    String value = responseObject.getString(key);
                    response.put(key, value);
                }
            } catch (JSONException e) {
                LOG.error("Failed to parse service response.", e);
            }
        }

        return response;
    }

}

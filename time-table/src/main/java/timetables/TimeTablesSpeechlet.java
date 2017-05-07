package timetables;


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
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.IOUtils;

public class TimeTablesSpeechlet implements Speechlet {

    private static final Logger LOG = LoggerFactory.getLogger(TimeTablesSpeechlet.class);

    private static final String CARD_TITLE = "Time Tables";
    private final HttpRequest wsRequest = new HttpRequest();
    private final Responder responder = new Responder(CARD_TITLE);
    private static final String SESSION_ISWHATNEXT = "isWhatNext";
    private static final String USER = System.getenv("user");
    private static final String PASSWD = System.getenv("passwd");
    private static final String URL = "http://api.reittiopas.fi/hsl/prod/?request=stop&user=" +
            USER + "&pass=" + PASSWD + "&format=json&code=";
    private static final String TRAIN_STOP = System.getenv("train_stop");
    private static final String BUS_STOP = System.getenv("bus_stop");
    private static final List<String> LINES = Arrays.asList(System.getenv("lines").split(","));
    
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

        if ("NextTrainIntent".equals(intentName)) {
            return handleNextTrainRequest(intent, session);
        } else if ("NextBusIntent".equals(intentName)) {
            return handleNextBusRequest(intent, session);
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
        String speechText = "You can ask " + CARD_TITLE + " about the next train or bus.";

        return responder.askResponse(speechText);
    }

    private SpeechletResponse handleNextTrainRequest(final Intent intent, final Session session) {
        String speechText;
        
        List<String> departures = getNextDepartures(TRAIN_STOP);
        if(departures.isEmpty()) {
            speechText = "No trains today.";
        } else {
            speechText = "Next train departures are at ";
            for(int i = 0; i < departures.size() - 1; i++) {
                if(i != 0) {
                    speechText += ", ";
                }
                speechText += departures.get(i);
            }
            speechText += " and " + departures.get(departures.size() - 1);
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(speechText, isWhatNext);
    }

    private SpeechletResponse handleNextBusRequest(final Intent intent, final Session session) {
        String speechText;
        
        List<String> departures = getNextDepartures(BUS_STOP);
        if(departures.isEmpty()) {
            speechText = "No buses today.";
        } else {
            speechText = "Next bus departures are at ";
            for(int i = 0; i < departures.size() - 1; i++) {
                if(i != 0) {
                    speechText += ", ";
                }
                speechText += departures.get(i);
            }
            speechText += " and " + departures.get(departures.size() - 1);
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(speechText, isWhatNext);
    }

    private List<String> getNextDepartures(String stop) {
        return getNextDepartures(stop, null);
    }

    private List<String> getNextDepartures(String stop, String line) {
        List<String> departures = new ArrayList<>();

        InputStreamReader inputStream = null;
        BufferedReader bufferedReader = null;
        StringBuilder builder = new StringBuilder();
        try {
            String row;
            URL url = new URL(URL + stop);
            inputStream = new InputStreamReader(url.openStream());
            bufferedReader = new BufferedReader(inputStream);
            while ((row = bufferedReader.readLine()) != null) {
                builder.append(row);
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
            try {
                JSONArray responseArray = new JSONArray(new JSONTokener(builder.toString()));
                JSONObject stopObject = responseArray.getJSONObject(0);
                JSONArray departuresArray = stopObject.getJSONArray("departures");
                for(int i = 0;i < departuresArray.length();i++) {
                    JSONObject departure = departuresArray.getJSONObject(i);
                    String lineCode = departure.getString("code");
                    if(LINES.contains(lineCode)) {
                        departures.add(toTime(departure.getString("time")));
                    }
                }
                
            } catch (JSONException e) {
                LOG.error("Failed to parse service response.", e);
            }
        }
        
        return departures;
    }
    
    private String toTime(String in) {
        String time = in;
        while(time.length() < 4) {
            time = "0" + time;
        }

        return time.substring(0, 2) + ":" + time.substring(2, 4);
    }
    
}

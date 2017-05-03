package homemeasurements;

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
import java.util.Map;

public class HomeMeasurementsSpeechlet implements Speechlet {

    private static final Logger log = LoggerFactory.getLogger(HomeMeasurementsSpeechlet.class);

    private static final String CARD_TITLE = "Home Measurements";
    private final HttpRequest wsRequest = new HttpRequest();
    private final Responder responder = new Responder(CARD_TITLE);
    private static final String SESSION_ISWHATNEXT = "isWhatNext";

    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // any initialization logic goes here
        session.setAttribute(SESSION_ISWHATNEXT, false);
    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        session.setAttribute(SESSION_ISWHATNEXT, true);
        return getWelcomeResponse();
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        Intent intent = request.getIntent();
        String intentName = (intent != null) ? intent.getName() : null;

        if ("GetTemperatureIntent".equals(intentName)) {
            return handleGetTemperatureRequest(intent, session);
        } else if ("GetPowerConsumptionIntent".equals(intentName)) {
            return handleGetPowerConsumptionRequest(intent, session);
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
        String speechText = "You can ask Home Measurements to tell "
                + "temperature or power consumption. "
                + "For example, you can say: what is temperature.";

        return responder.askResponse(speechText);
    }

    private SpeechletResponse handleGetTemperatureRequest(final Intent intent, final Session session) {
        Map<String, String> measurements = wsRequest.send("GetMeasurements");

        StringBuilder sb = new StringBuilder();
        if (measurements != null) {
            sb.append("Downstairs ");
            sb.append(measurements.get("downstairs_temperature"));
            sb.append(". Upstairs ");
            sb.append(measurements.get("upstairs_temperature"));
            sb.append(". Outside  ");
            sb.append(measurements.get("outside_temperature"));
            sb.append(".");
        } else {
            sb.append("Failed to connect to Home Control.");
        }
        String speechText = sb.toString();

        boolean isWhatNext = (boolean)session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(speechText, isWhatNext);
    }

    private SpeechletResponse handleGetPowerConsumptionRequest(final Intent intent, final Session session) {
        Map<String, String> measurements = wsRequest.send("GetMeasurements");

        StringBuilder sb = new StringBuilder();
        if (measurements != null) {
            sb.append("Total power ");
            sb.append(measurements.get("total_power"));
            sb.append(". Heat pump ");
            sb.append(measurements.get("heat_pump_power"));
            sb.append(". Floor heating ");
            sb.append(measurements.get("floor_heating_power"));
            sb.append(". Water heating ");
            sb.append(measurements.get("water_power"));
            sb.append(". Ventillation ");
            sb.append(measurements.get("ventillation_power"));
            sb.append(".");
        } else {
            sb.append("Failed to connect to Home Control.");
        }
        String speechText = sb.toString();

        boolean isWhatNext = (boolean)session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(speechText, isWhatNext);
    }

}

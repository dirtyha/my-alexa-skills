package heatingcontrol;

import java.util.HashMap;

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
import java.util.Map;

public class HeatingControlSpeechlet implements Speechlet {

    private static final Logger LOG = LoggerFactory.getLogger(HeatingControlSpeechlet.class);
    private static final String CARD_TITLE = "Heating Control";
    private static final String SLOT_ONOFF = "onoff";
    private static final String SLOT_TEMPERATURE = "temperature";
    private static final String SLOT_FAN = "fan";
    private static final String SLOT_MODE = "mode";
    private static final String SESSION_ISWHATNEXT = "isWhatNext";

    private final HttpRequest wsRequest = new HttpRequest();
    private final Responder responder = new Responder(CARD_TITLE);

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

        if ("GetPumpParametersIntent".equals(intentName)) {
            return handlePumpParametersRequest(intent, session);
        } else if ("GetPumpCopIntent".equals(intentName)) {
            return handlePumpCopRequest(intent, session);
        } else if ("SetPumpPowerIntent".equals(intentName)) {
            return handlePumpPowerRequest(intent, session);
        } else if ("SetPumpTemperatureIntent".equals(intentName)) {
            return handlePumpTemperatureRequest(intent, session);
        } else if ("SetPumpFanIntent".equals(intentName)) {
            return handlePumpFanRequest(intent, session);
        } else if ("SetPumpModeIntent".equals(intentName)) {
            return handlePumpModeRequest(intent, session);
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
        String speechText = "You can ask Heating Control to set "
                + "power, temperature, mode and fan speed to the heat pump. "
                + "For example, you can say: set heat pump temperature 22.";

        return responder.askResponse(speechText);
    }

    private SpeechletResponse handlePumpParametersRequest(final Intent intent, final Session session) {
        Map<String, String> measurements = wsRequest.send("GetHeatPump");

        StringBuilder sb = new StringBuilder();
        if (measurements != null) {
            String power = measurements.get("power");
            if (power != null) {
                sb.append("Power ");
                sb.append(power);
                sb.append(", ");
            }

            String mode = measurements.get("mode");
            if (mode != null) {
                sb.append("mode ");
                sb.append(mode);
                sb.append(", ");
            }

            String temperature = measurements.get("temperature");
            if (temperature != null) {
                sb.append("temperature ");
                sb.append(temperature);
                sb.append(", ");
            }

            String fan = measurements.get("fan");
            if (fan != null) {
                sb.append("fan speed ");
                sb.append(fan.substring(1));
                sb.append(".");
            }
        } else {
            sb.append(HttpRequest.CONNECTION_FAILURE_TEXT);
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(sb.toString(), isWhatNext);
    }

    private SpeechletResponse handlePumpCopRequest(final Intent intent, final Session session) {
        Map<String, String> measurements = wsRequest.send("GetMeasurements");

        StringBuilder sb = new StringBuilder();
        if (measurements != null) {
            String cop = measurements.get("heat_pump_cop");
            if (cop.equals("NaN")) {
                sb.append("Heat pump is not on.");
            } else {
                sb.append("C.O.P. ");
                sb.append(cop);
            }
        } else {
            sb.append(HttpRequest.CONNECTION_FAILURE_TEXT);
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(sb.toString(), isWhatNext);
    }

    private SpeechletResponse handlePumpPowerRequest(final Intent intent, final Session session) {
        String speechText;
        Slot onoffSlot = intent.getSlot(SLOT_ONOFF);

        if (onoffSlot == null || onoffSlot.getValue() == null) {
            speechText = "You must specify power on or off.";
        } else {
            String onoff = onoffSlot.getValue();

            Map<String, String> parameters = new HashMap<>();
            parameters.put("power", onoff);
            if (wsRequest.send("SetHeatPump", parameters) != null) {
                speechText = onoff;
            } else {
                speechText = HttpRequest.CONNECTION_FAILURE_TEXT;
            }
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(speechText, isWhatNext);
    }

    private SpeechletResponse handlePumpTemperatureRequest(final Intent intent, final Session session) {
        String speechText;
        Slot temperatureSlot = intent.getSlot(SLOT_TEMPERATURE);

        if (temperatureSlot == null || temperatureSlot.getValue() == null) {
            speechText = "You must specify temperature between 16 and 31.";
        } else {
            String temperature = temperatureSlot.getValue();

            Map<String, String> parameters = new HashMap<>();
            parameters.put("temperature", temperature);
            if (wsRequest.send("SetHeatPump", parameters) != null) {
                speechText = temperature;
            } else {
                speechText = HttpRequest.CONNECTION_FAILURE_TEXT;
            }
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(speechText, isWhatNext);
    }

    private SpeechletResponse handlePumpModeRequest(final Intent intent, final Session session) {
        String speechText;
        Slot modeSlot = intent.getSlot(SLOT_MODE);

        if (modeSlot == null || modeSlot.getValue() == null) {
            speechText = "You must specify mode heating or cooling.";
        } else {
            String mode = modeSlot.getValue();

            Map<String, String> parameters = new HashMap<>();
            parameters.put("mode", mode);
            if (wsRequest.send("SetHeatPump", parameters) != null) {
                speechText = mode;
            } else {
                speechText = HttpRequest.CONNECTION_FAILURE_TEXT;
            }
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(speechText, isWhatNext);
    }

    private SpeechletResponse handlePumpFanRequest(final Intent intent, final Session session) {
        String speechText;
        Slot fanSlot = intent.getSlot(SLOT_FAN);

        if (fanSlot == null || fanSlot.getValue() == null) {
            speechText = "You must specify fan speed 1, 2, 3 or 4.";
        } else {
            String fan = fanSlot.getValue();

            Map<String, String> parameters = new HashMap<>();
            parameters.put("fan", "F" + fan);
            if (wsRequest.send("SetHeatPump", parameters) != null) {
                speechText = fan;
            } else {
                speechText = HttpRequest.CONNECTION_FAILURE_TEXT;
            }
        }

        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(speechText, isWhatNext);
    }
}

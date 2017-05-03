package lightscontrol;

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

public class LightsControlSpeechlet implements Speechlet {

    private static final Logger LOG = LoggerFactory.getLogger(LightsControlSpeechlet.class);

    private static final String CARD_TITLE = "Lights Control";
    private static final String SLOT_ROOM = "room";
    private static final Map<String, String> ROOMS = new HashMap<>();
    private final HttpRequest wsRequest = new HttpRequest();
    private final Responder responder = new Responder(CARD_TITLE);
    private static final String SESSION_ISWHATNEXT = "isWhatNext";
    
    static {
        ROOMS.put("entry", "entry");
        ROOMS.put("hall", "hall");
        ROOMS.put("kitchen", "kitchen");
        ROOMS.put("kitchen cabinets", "kitchen_cabinets");
        ROOMS.put("bay", "bay");
        ROOMS.put("office", "office");
        ROOMS.put("master bathroom", "master_bathroom");
        ROOMS.put("utility room", "utility_room");
        ROOMS.put("washing room", "washing_room");
        ROOMS.put("sauna", "sauna_fiber");
        ROOMS.put("sauna wall", "sauna_wall");
        ROOMS.put("hall upstairs", "hall_upstairs");
        ROOMS.put("master bedroom", "master_bedroom");
        ROOMS.put("master bedroom wall", "master_bedroom_wall");
        ROOMS.put("big bedroom", "big_bedroom");
        ROOMS.put("small bedroom", "small_bedroom");
        ROOMS.put("bathroom", "bathroom");
        ROOMS.put("bathroom cabinet", "bathroom_cabinet");
        ROOMS.put("stairs", "stairs");
        ROOMS.put("porch", "porch");
        ROOMS.put("terrace", "terrace");
        ROOMS.put("outer wall", "outer_wall");
        ROOMS.put("yard", "yard");
        ROOMS.put("living room", "livingroom");
        ROOMS.put("downstairs", "downstairs");
        ROOMS.put("upstairs", "upstairs");
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

        if ("LightsOnIntent".equals(intentName)) {
            return handleLightsOnRequest(intent, session);
        } else if ("LightsOffIntent".equals(intentName)) {
            return handleLightsOffRequest(intent, session);
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
        String speechText = "You can ask " + CARD_TITLE + " to switch lights on or off "
                + " in a room. "
                + "For example, you can say lights off in livingroom.";

        return responder.askResponse(speechText);
    }

    private SpeechletResponse handleLightsOnRequest(final Intent intent, final Session session) {
        String speechText;
        Slot roomSlot = intent.getSlot(SLOT_ROOM);

        if (roomSlot == null || roomSlot.getValue() == null) {
            speechText = "You must specify room.";
        } else {
            String room = roomSlot.getValue();

            String encodedRoom = ROOMS.get(room);
            if (encodedRoom != null) {
                Map<String, String> parameters = new HashMap<>();
                parameters.put("room", encodedRoom);
                parameters.put("power", "on");
                if (wsRequest.send("SetLights", parameters) != null) {
                    speechText = "Done.";
                } else {
                    speechText = "Failed to connect to we service.";
                }
            } else {
                speechText = room + " is not a room.";
            }
        }
        
        boolean isWhatNext = (boolean)session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(speechText, isWhatNext);
    }

    private SpeechletResponse handleLightsOffRequest(final Intent intent, final Session session) {
        String speechText;
        Slot roomSlot = intent.getSlot(SLOT_ROOM);

        if (roomSlot == null || roomSlot.getValue() == null) {
            speechText = "You must specify room.";
        } else {
            String room = roomSlot.getValue();

            String encodedRoom = ROOMS.get(room);
            if (encodedRoom != null) {
                Map<String, String> parameters = new HashMap<>();
                parameters.put("room", encodedRoom);
                parameters.put("power", "off");
                if (wsRequest.send("SetLights", parameters) != null) {
                    speechText = "Done.";
                } else {
                    speechText = "Failed to connect to web service.";
                }
            } else {
                speechText = room + " is not a room.";
            }
        }

        boolean isWhatNext = (boolean)session.getAttribute(SESSION_ISWHATNEXT);
        return responder.respond(speechText, isWhatNext);
    }

}

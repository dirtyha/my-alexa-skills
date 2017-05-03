package homecontrol;

import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.amazon.speech.ui.SsmlOutputSpeech;

/**
 *
 * @author harri
 */
public class Responder {

    private static final int BT_DELAY = 1; // bluetooth delay in s
    private final String cardTitle;

    public Responder(String cardTite) {
        this.cardTitle = cardTite;
    }

    public SpeechletResponse tellResponse(String speechText) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle(cardTitle);
        card.setContent(speechText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        return SpeechletResponse.newTellResponse(speech, card);
    }

    public SpeechletResponse tellResponseNoCard(String speechText) {
        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        return SpeechletResponse.newTellResponse(speech);
    }

    public SpeechletResponse askResponse(String speechText) {
        return askResponse(speechText, null);
    }

    public SpeechletResponse askResponse(String speechText, String repromptText) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle(cardTitle);
        card.setContent(speechText);

        // Must add delay here because of bluetooth latency which causes Alexa response
        // to loop back to Alexa
        SsmlOutputSpeech speech = new SsmlOutputSpeech();
        speech.setSsml("<speak>" + speechText + " <break time=\"" + BT_DELAY + "s\"/> </speak>");

        SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
        repromptSpeech.setSsml("<speak>" + repromptText + " <break time=\"" + BT_DELAY + "s\"/> </speak>");
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptSpeech);

        return SpeechletResponse.newAskResponse(speech, reprompt, card);
    }

    public SpeechletResponse respond(String speechText, boolean isWhatNext) {
        if (isWhatNext) {
            return askResponse(speechText, "What next?");
        } else {
            return tellResponse(speechText);
        }
    }
}

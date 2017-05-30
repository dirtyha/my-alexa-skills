package finnkino;

import java.util.HashSet;
import java.util.Set;

import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.lambda.SpeechletRequestStreamHandler;

/**
 * This class could be the handler for an AWS Lambda function powering an Alexa Skills Kit experience.
 */
public class FinnkinoSpeechletRequestStreamHandler extends SpeechletRequestStreamHandler {

    private static final Set<String> supportedApplicationIds;

    static {
        /*
         * This Id can be found on https://developer.amazon.com/edw/home.html#/ "Edit" the relevant
         * Alexa Skill and put the relevant Application Ids in this Set.
         */
        supportedApplicationIds = new HashSet<String>();
//        String appId = System.getenv("application_id");
//        supportedApplicationIds.add(appId);
    }

    public FinnkinoSpeechletRequestStreamHandler() {
        super(new FinnkinoSpeechlet(), supportedApplicationIds);
    }

    public FinnkinoSpeechletRequestStreamHandler(Speechlet speechlet,
            Set<String> supportedApplicationIds) {
        super(speechlet, supportedApplicationIds);
    }

}

package lunchtime;

import java.util.*;

import javax.mail.*;
import javax.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostMan {

    private static final Logger log = LoggerFactory.getLogger(PostMan.class);

    private static final String SMPT_HOSTNAME = System.getenv("smtp_host_name");
    private static final String USERNAME = System.getenv("smtp_username");
    private static final String PASSWORD = System.getenv("smtp_password");
    private static final String FROM = "Alexa@koti.com";

    public static boolean send(List<String> toList, String subject, String body) {
        boolean isOk = false;
        // Get system properties
        Properties properties = System.getProperties();

        // Setup mail server
        properties.setProperty("mail.smtp.host", SMPT_HOSTNAME);
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.EnableSSL.enable", "true");
        properties.setProperty("mail.smtp.ssl.trust", "*");
        properties.setProperty("mail.smtp.auth", "true");

        // create a session with an Authenticator
        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(USERNAME, PASSWORD);
            }
        });

        try {
            // Create a default MimeMessage object.
            MimeMessage message = new MimeMessage(session);

            // Set From: header field of the header.
            message.setFrom(new InternetAddress(FROM));

            // Set To: header field of the header.
            for (String to : toList) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            }

            // Set Subject: header field
            message.setSubject(subject);

            // Now set the actual message
            if (body != null && body.length() > 0) {
                message.setText(body);
            }

            // Send message
            Transport.send(message);
            isOk = true;
        } catch (MessagingException e) {
            log.error("Failed to send email.", e);
        }

        return isOk;
    }
}

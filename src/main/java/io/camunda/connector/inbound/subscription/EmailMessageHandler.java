package io.camunda.connector.inbound.subscription;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeBodyPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Consumer;

public class EmailMessageHandler {
    private final static Logger LOG = LoggerFactory.getLogger(EmailMessageHandler.class);

    public static void parseEmailAndSendtoCamunda(Message message, Consumer<EmailWatchServiceSubscriptionEvent> callback) {
        try {
            LOG.info("From " + message.getFrom());
            LOG.info("Reply to " + message.getReplyTo());
            LOG.info("Subject " + message.getSubject());

            Object[] emailBody = null;

            Object content = message.getContent();
            if (content instanceof String) {
                LOG.info("Body " + content);
                emailBody = new Object[]{content};
            } else if (content instanceof Multipart multiPart) {


                ArrayList parts = new ArrayList<Object>();




                int partCount = multiPart.getCount();
                LOG.info("processing multipart message with {} parts", partCount);
                for (int i = 0; i < partCount; i++) {
                    MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(i);


                    if (part.isMimeType("text/plain")) {
                        LOG.info("mimtype of part {} is text/plain", i+1);
                        parts.add(part.getContent());
                        LOG.info("Body {}", part.getContent());
                    }
                    // store attachments
                    if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                        String file = part.getFileName();
                        LOG.info("mimtype of part {} is {} and filename is {}", i+1, part.getDisposition(), file);
                        part.saveFile("." + File.separator + part.getFileName());
                        parts.add(file);
                    }


                }
                emailBody = parts.toArray(Object[]::new);
            }


            EmailWatchServiceSubscriptionEvent ewsse = new EmailWatchServiceSubscriptionEvent(message.getFrom(), message.getReplyTo(), message.getSubject(), emailBody);
            callback.accept(ewsse);
        } catch (Exception e) {
            e.printStackTrace();
            //todo this should perhaps rethrow so emails can be picket up later again.
        }

    }
}

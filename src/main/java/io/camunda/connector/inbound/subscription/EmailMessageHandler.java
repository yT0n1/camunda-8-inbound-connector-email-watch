package io.camunda.connector.inbound.subscription;

import com.google.auth.oauth2.GoogleCredentials;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeBodyPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

public class EmailMessageHandler {
  private final static Logger LOG = LoggerFactory.getLogger(EmailMessageHandler.class);

  public static void parseEmailAndSendtoCamunda(Message message, String projectID, String bucketName,
      Consumer<EmailWatchServiceSubscriptionEvent> callback) {
    try {
      LOG.info("From " + message.getFrom());
      LOG.info("Reply to " + message.getReplyTo());
      LOG.info("Subject " + message.getSubject());

      Object[] emailBody = null;
      Object[] gcpUploads = null;

      Object content = message.getContent();
      if (content instanceof String) {
        LOG.info("Body " + content);
        emailBody = new Object[] { content };
      } else if (content instanceof Multipart multiPart) {

        ArrayList parts = new ArrayList<Object>();
        ArrayList uploads = new ArrayList<Object>();

        int partCount = multiPart.getCount();
        LOG.info("processing multipart message with {} parts", partCount);
        for (int i = 0; i < partCount; i++) {
          MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(i);

          if (part.isMimeType("text/plain")) {
            LOG.info("mimtype of part {} is text/plain", i + 1);
            parts.add(part.getContent());
            LOG.info("Body {}", part.getContent());
          }
          // store attachments
          if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            String file = part.getFileName();
            LOG.info("mimtype of part {} is {} and filename is {}", i + 1, part.getDisposition(), file);
            part.saveFile("." + File.separator + part.getFileName());
            parts.add(file);
            uploads.add(uploadObject(projectID,bucketName,part.getInputStream()));
          }

        }
        emailBody = parts.toArray(Object[]::new);
        gcpUploads = uploads.toArray(Object[]::new);
      }

      EmailWatchServiceSubscriptionEvent ewsse = new EmailWatchServiceSubscriptionEvent(message.getFrom(),
          message.getReplyTo(), message.getSubject(), emailBody, gcpUploads);
      callback.accept(ewsse);
    } catch (Exception e) {
      e.printStackTrace();
      //todo this should perhaps rethrow so emails can be picket up later again.
    }

  }

  public static BlobId uploadObject(String projectId, String bucketName, InputStream fileStream)
      throws IOException {
    var objectName = UUID.randomUUID().toString();


    var credentials = GoogleCredentials.getApplicationDefault();
    Storage storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(credentials).build().getService();
    BlobId blobId = BlobId.of(bucketName, objectName);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

    // Optional: set a generation-match precondition to avoid potential race
    // conditions and data corruptions. The request returns a 412 error if the
    // preconditions are not met.
    Storage.BlobWriteOption precondition;
    if (storage.get(bucketName, objectName) == null) {
      // For a target object that does not yet exist, set the DoesNotExist precondition.
      // This will cause the request to fail if the object is created before the request runs.
      precondition = Storage.BlobWriteOption.doesNotExist();
    } else {
      // If the destination already exists in your bucket, instead set a generation-match
      // precondition. This will cause the request to fail if the existing object's generation
      // changes before the request runs.
      precondition = Storage.BlobWriteOption.generationMatch(storage.get(bucketName, objectName).getGeneration());
    }
    var completeBlogInfo = storage.createFrom(blobInfo, fileStream, precondition);
    System.out.println("File uploaded to bucket " + bucketName + " as " + objectName);
    return completeBlogInfo.getBlobId();
  }

}

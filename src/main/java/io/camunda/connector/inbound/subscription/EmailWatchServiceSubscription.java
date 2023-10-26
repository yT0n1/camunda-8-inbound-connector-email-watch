package io.camunda.connector.inbound.subscription;

import io.camunda.connector.inbound.MyConnectorProperties;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.FolderClosedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.event.MessageCountAdapter;
import jakarta.mail.event.MessageCountEvent;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.function.Consumer;

public class EmailWatchServiceSubscription implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(EmailWatchServiceSubscription.class);
  private final Consumer<EmailWatchServiceSubscriptionEvent> callback;
  private final MyConnectorProperties connectorProperties;
  private volatile boolean isRunning = true;
  private final Session session;
  private final IMAPFolder imapFolder;

  public EmailWatchServiceSubscription(MyConnectorProperties connectorProperties,
      Consumer<EmailWatchServiceSubscriptionEvent> callback) throws MessagingException {

    Properties props = new Properties();
    props.setProperty("mail.store.protocol", "imaps");
    props.setProperty("mail.imaps.host", connectorProperties.getUrl());
    props.setProperty("mail.imaps.port", connectorProperties.getPort());
    props.setProperty("mail.imaps.connectiontimeout", connectorProperties.getTimeout());
    //props.setProperty("mail.imaps.usesocketchannels", "true");

    this.connectorProperties = connectorProperties;
    this.callback = callback;

    try {
      LOG.info("running subscription thread");

      this.session = Session.getDefaultInstance(props);
      //session.setDebug(true);
      Store store = session.getStore();
      store.connect(connectorProperties.getUsername(), connectorProperties.getPassword());
      this.imapFolder = (IMAPFolder) store.getFolder(connectorProperties.getFolder());

      imapFolder.open(Folder.READ_WRITE);
    } catch (MessagingException e) {
      LOG.error("Could not connect to IMAP Folder");
      LOG.debug("Used props {}", props);
      throw e;
    }

  }

  public void stop() {
    LOG.info("Deactivating email watch subscription");
    this.isRunning = false;

  }

  @Override
  public void run() {

    CheckEmailFolder.searchForUnreadEmails(this.imapFolder, connectorProperties.getGcsProject(),
        connectorProperties.getGcsBucketName(), callback);


    var projectID = connectorProperties.getGcsProject();
    var bucketName = connectorProperties.getGcsBucketName();

    LOG.info("Start long poll cycle");
    imapFolder.addMessageCountListener(new MessageCountAdapter() {
      @Override
      public void messagesAdded(MessageCountEvent ev) {
        Folder folder = (Folder) ev.getSource();
        Message[] msgs = ev.getMessages();
        LOG.info("Email(s) received in folder: " + folder + " with " + msgs.length + " new message(s)");

        for (Message message : msgs) {
          EmailMessageHandler.parseEmailAndSendtoCamunda(message, projectID, bucketName, callback);
        }


        try {
          // set msgs to SEEN and process new messages
          folder.setFlags(msgs, new Flags(Flags.Flag.SEEN), true);
        } catch (MessagingException mex) {
          LOG.error("Error on starting new watch", mex);
        }
      }
    });
    boolean supportsIdle = false;
    try {
      try {
          this.imapFolder.idle();
          supportsIdle = false;
      } catch (FolderClosedException fex) {
        LOG.error("Folder closed, aborting",fex);
        throw fex;
      } catch (MessagingException mex) {
        supportsIdle = false;
      }
      while (this.isRunning) {
        if (supportsIdle) {
          LOG.info("Starting IDLE ");
          this.imapFolder.idle();
          LOG.info("IDLE done");
        } else {
          LOG.info("Starting Sleep ");
          Thread.sleep(10000);// sleep before polling again
          LOG.info("Sleep Done");
          // This is to force the IMAP server to send us
          // EXISTS notifications.
          this.imapFolder.getMessageCount();
        }
      }
    } catch (MessagingException e) {
      LOG.error("Error while monitoring Imap folder", e);
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      LOG.error("Email Subscribtion Thread interrupted", e);
      throw new RuntimeException(e);
    }

    LOG.info("Deactivated subscription");
    try {
      this.imapFolder.close();
      this.session.getStore().close();
    } catch (MessagingException e) {
      LOG.error("Issues on resource release",e);
      throw new RuntimeException("Issues on resource release",e);
    }

  }
}

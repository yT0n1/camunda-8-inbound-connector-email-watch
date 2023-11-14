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
import org.apache.commons.logging.Log;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.function.Consumer;

public class EmailWatchServiceSubscription implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(EmailWatchServiceSubscription.class);
  private final Consumer<EmailWatchServiceSubscriptionEvent> callback;
  private final MyConnectorProperties connectorProperties;
  private final Properties props;
  private volatile boolean isRunning = true;

  public EmailWatchServiceSubscription(MyConnectorProperties connectorProperties,
      Consumer<EmailWatchServiceSubscriptionEvent> callback) throws MessagingException {

    this.props = new Properties();
    props.setProperty("mail.store.protocol", "imaps");
    props.setProperty("mail.imaps.host", connectorProperties.getUrl());
    props.setProperty("mail.imaps.port", connectorProperties.getPort());
    props.setProperty("mail.imaps.connectiontimeout", connectorProperties.getTimeout());
    //props.setProperty("mail.imaps.usesocketchannels", "true");

    this.connectorProperties = connectorProperties;
    this.callback = callback;


  }

  private IMAPFolder connect() throws MessagingException {
    try {
      LOG.info("Connecting to imap folder ");

      var session = Session.getInstance(props);
      LOG.info("creating session {}", session);
      //session.setDebug(true);
      Store store = session.getStore();
      LOG.info("creating store {}", store.hashCode());
      store.connect(connectorProperties.getUsername(), connectorProperties.getPassword());
      var imapFolder = (IMAPFolder) store.getFolder(connectorProperties.getFolder());
      LOG.info("creating folder {}", imapFolder.hashCode());

      imapFolder.open(Folder.READ_WRITE);

      return imapFolder;
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

    /*CheckEmailFolder.searchForUnreadEmails(this.imapFolder, connectorProperties.getGcsProject(),
        connectorProperties.getGcsBucketName(), connectorProperties.getPath(), callback);*/


    var projectID = connectorProperties.getGcsProject();
    var bucketName = connectorProperties.getGcsBucketName();

    while (this.isRunning){
      try (var imapFolder = connect()) {
        imapFolder.addMessageCountListener(new MessageCountAdapter() {
          @Override
          public void messagesAdded(MessageCountEvent ev) {
            Folder folder = (Folder) ev.getSource();
            Message[] msgs = ev.getMessages();
            LOG.info("Email(s) received in folder: " + folder + " with " + msgs.length + " new message(s)");

            for (Message message : msgs) {
              EmailMessageHandler.parseEmailAndSendtoCamunda(message, projectID, bucketName, connectorProperties.getPath(), callback);
            }
          }
        });
        while (this.isRunning) {
          var count = imapFolder.getMessageCount();
          LOG.info("current unread count {}", count);
          LOG.info("Starting Sleep ");
          Thread.sleep(10000);// sleep before polling again
          LOG.info("Sleep Done");
        }
      } catch (MessagingException e) {
        LOG.warn("Failed to connect to Imap server", e);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ex) {
          throw new RuntimeException(ex);
        }
        //throw new RuntimeException(e);
      } catch (InterruptedException e) {
        LOG.warn("Interrupted during sleep");
        throw new RuntimeException(e);
      }

    }
    LOG.info("Deactivating Subscription");

  }
}

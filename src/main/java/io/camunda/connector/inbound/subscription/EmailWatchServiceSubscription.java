package io.camunda.connector.inbound.subscription;

import io.camunda.connector.inbound.MyConnectorProperties;
import jakarta.mail.*;
import org.awaitility.Awaitility;
import org.eclipse.angus.mail.imap.IdleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class EmailWatchServiceSubscription implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(EmailWatchServiceSubscription.class);
    private final Consumer<EmailWatchServiceSubscriptionEvent> callback;
    private final MyConnectorProperties connectorProperties;

    public EmailWatchServiceSubscription(MyConnectorProperties connectorProperties, Consumer<EmailWatchServiceSubscriptionEvent> callback) {

            this.connectorProperties = connectorProperties;
            this.callback = callback;

    }

    public void stop() {
        LOG.info("Deactivating email watch service");
    }

    @Override
    public void run() {
        try {
            LOG.info("running subscription thread");
            Properties props = new Properties();
            props.setProperty("mail.store.protocol", "imaps");
            props.setProperty("mail.imaps.host", connectorProperties.getUrl());
            props.setProperty("mail.imaps.port", connectorProperties.getPort());
            props.setProperty("mail.imaps.connectiontimeout", connectorProperties.getTimeout());
            props.setProperty("mail.imaps.usesocketchannels", "true");

            Session session = Session.getDefaultInstance(props);
            Store store = session.getStore();
            store.connect(connectorProperties.getUsername(), connectorProperties.getPassword());

            Folder imapFolder = store.getFolder(connectorProperties.getFolder());
            imapFolder.open(Folder.READ_WRITE);
            CheckEmailFolder.searchForUnreadEmails(imapFolder, connectorProperties.getGcsProject(), connectorProperties.getGcsBucketName(), callback);
            while (true) {
                //imapFolder.close();
                imapFolder = store.getFolder(connectorProperties.getFolder());
                imapFolder.open(Folder.READ_WRITE);
                ExecutorService es = Executors.newCachedThreadPool();
                IdleManager idleManager = new IdleManager(session, es);
                IdleManagerHandler imapmh = new IdleManagerHandler(idleManager, imapFolder,
                    connectorProperties.getGcsBucketName(),connectorProperties.getGcsProject(), callback);
                imapmh.run();
                Awaitility.await()
                    .timeout(Duration.ofSeconds(Long.parseLong(connectorProperties.getPollingInterval())))
                    .pollDelay(Duration.ofSeconds(Long.parseLong(connectorProperties.getPollingInterval()) - 1))
                    .until(() -> true);
                idleManager.stop();
                es.shutdown();
            }
        } catch (MessagingException | IOException e) {
            LOG.warn(e.toString());
            throw new RuntimeException(e);
        }
    }
}

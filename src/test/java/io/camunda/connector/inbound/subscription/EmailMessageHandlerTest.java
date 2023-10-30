package io.camunda.connector.inbound.subscription;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.activation.*;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
class EmailMessageHandlerTest {



  @BeforeEach
  void setUp() {

  }

  @AfterEach
  void tearDown() {
  }

  //@Test todo, update to new signature
  void shouldParseEmail() {
    var anEmail = generateMessage();

    //EmailMessageHandler.parseEmailAndSendtoCamunda(anEmail, EmailWatchServiceSubscriptionEvent -> {});
  }

  //@Test This test will do an actual upload
  void shouldUploadDocument() throws MessagingException, IOException {
    var anEmail = generateMessage();
    var projectID = "antonvonweltzien";
    var bucketName = "cam_email_attachements";

    MimeBodyPart part = (MimeBodyPart) ((Multipart)anEmail.getContent()).getBodyPart(0);
    EmailMessageHandler.uploadObject(projectID, bucketName, "","text",part.getInputStream());
  }

  Message generateMessage() {
    try {
      // Create a new text file with some content
      String textContent = "This is the content of the generated text file.";
      File textFile = new File("generated_textfile.txt");
      FileWriter fileWriter = new FileWriter(textFile);
      fileWriter.write(textContent);
      fileWriter.close();

      // Create a JavaMail session and message
      Session session = Session.getDefaultInstance(new Properties());
      Message message = new MimeMessage(session);

      // Set sender and recipient email addresses
      message.setFrom(new InternetAddress("sender@example.com"));
      message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("recipient@example.com"));

      // Set the subject of the email
      message.setSubject("Sample Email with Attachment");

      // Create a multipart message
      Multipart multipart = new MimeMultipart();

      // Create and add the text part to the message
      BodyPart textPart = new MimeBodyPart();
      textPart.setText("This is the text content of the email.");
      multipart.addBodyPart(textPart);

      // Create and add the attachment
      BodyPart attachmentPart = new MimeBodyPart();
      DataSource source = new FileDataSource(textFile);
      attachmentPart.setDataHandler(new DataHandler(source));
      attachmentPart.setFileName(textFile.getName());
      multipart.addBodyPart(attachmentPart);

      // Set the content of the message to the multipart content
      message.setContent(multipart);

      // Print the entire message
      System.out.println("Full Email Message:");
      message.writeTo(System.out);


      return message;
    } catch (IOException | AddressException e) {
      e.printStackTrace();
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
    return null;
  }

}
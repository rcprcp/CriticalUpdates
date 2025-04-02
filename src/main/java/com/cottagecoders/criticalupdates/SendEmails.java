package com.cottagecoders.criticalupdates;

import com.mailjet.client.ClientOptions;
import com.mailjet.client.MailjetClient;
import com.mailjet.client.errors.MailjetException;
import com.mailjet.client.transactional.SendContact;
import com.mailjet.client.transactional.SendEmailsRequest;
import com.mailjet.client.transactional.TransactionalEmail;
import com.mailjet.client.transactional.response.SendEmailsResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SendEmails {

  private final MailjetClient client;
  private static final Logger LOG = LogManager.getLogger(SendEmails.class);

  SendEmails() {
    ClientOptions options =
            ClientOptions.builder().apiKey(System.getenv("MJ_APIKEY_PUBLIC")).apiSecretKey(System.getenv(
            "MJ_APIKEY_PRIVATE")).build();
    client = new MailjetClient(options);

  }

  void send(String recipientEmail, String recipientName, String senderEmail, String senderName, String subject,
                 String body) {

    TransactionalEmail email2 = TransactionalEmail.builder()
                                        .to(new SendContact(recipientEmail, recipientName))
                                        .from(new SendContact(senderEmail, senderName))
                                   //     .cc(new SendContact("support-leadership@dremio.com", "Support Leadership"))
                                        .subject(subject)
                                        .htmlPart(body)
                                        .build();

    SendEmailsRequest request = SendEmailsRequest.builder().message(email2) // you can add up to 50 messages per request
                                        .build();
    try {
      SendEmailsResponse response = request.sendWith(client);
      LOG.info(String.format("Sent email %s %s %s %s %s %s",
                             recipientEmail,
                             recipientName,
                             senderEmail,
                             senderName,
                             subject,
                             body));
    } catch (MailjetException ex) {
      LOG.error(String.format("Error sending email %s %s %s %s %s %s",
                              recipientEmail,
                              recipientName,
                              senderEmail,
                              senderName,
                              subject,
                              body,
                              ex));

    }
  }
}



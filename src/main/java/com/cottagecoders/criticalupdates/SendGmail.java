package com.cottagecoders.criticalupdates;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.beans.PropertyEditorSupport;
import java.util.List;
import java.util.Properties;

public class SendGmail {
  private static final Logger LOG = LogManager.getLogger(SendGmail.class);

  void sendGmail(List<String> recipients, List<String> ccs, String subject, String body) {
    String senderEmail = System.getenv("GMAIL_SENDER_EMAIL");
    String senderAppPassword = System.getenv("GMAIL_SENDER_PASSWORD");

    Properties prop = new Properties();
    prop.put("mail.smtp.host", "smtp.gmail.com");
    prop.put("mail.smtp.port", "465");
    prop.put("mail.smtp.auth", "true");
    prop.put("mail.smtp.socketFactory.port", "465");
    prop.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

    Session session = Session.getInstance(prop, new jakarta.mail.Authenticator() {
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(senderEmail, senderAppPassword);
      }
    });

    try {

      Message message = new MimeMessage(session);
      message.setFrom(new InternetAddress(senderEmail));

      if(recipients.isEmpty()) {
        LOG.error("no recipients for email with subject '{}'", subject);
        return;
      }

      for(String s: recipients) {
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(s));
      }

      for(String s: ccs) {
        message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(s));
      }

      message.setSubject(subject);
      message.setText(body);

      Transport.send(message);
      LOG.info("Sent Gmail to {} with CCs {} and Subject {}", recipients, ccs, subject);

    } catch (MessagingException ex) {
      LOG.error("Exception: {}", ex.getMessage(), ex);
    }
  }

}

package com.cottagecoders.criticalupdates;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.Comment;
import org.zendesk.client.v2.model.Organization;
import org.zendesk.client.v2.model.Status;
import org.zendesk.client.v2.model.Ticket;
import org.zendesk.client.v2.model.User;

import java.util.List;

public class Escalate {
  private static final Logger LOG = LogManager.getLogger(Escalate.class);
  private static final long ESCALATION_INTERVAL = 86400;

  private static final String CANNOT_ESCALATE = "cannot_escalate";

  private static final String ESCALATION_TAG = "escalation_";
  private static final String ORGANIZATION_SUPPORT_LEVEL = "sfdc_support_level";
  private static final String BRONZE_SUPPORT = "bronze_support";
  private static final String SILVER_SUPPORT = "silver_support";
  private static final String ESCALATION_REQUEST = "[**Escalation Request**]";
  private static final String NOT_ACCEPTED = "Escalation not accepted.  ";
  private static final String TOO_SOON =
          NOT_ACCEPTED + "This ticket was escalated %d minutes ago.  Tickets can be " + "escalated only once per " +
                  "24-hour " + "period.";
  private static final String NO_ESCALATION_FOR_BRONZE_SUPPORT = NOT_ACCEPTED + "Only Gold Support customers are " +
                                                                         "eligible to use the 'Escalate' button.";
  private static final String ON_CALL_ENGINEER = "This ticket has been escalated to your TAM (Technical Account " +
                                                         "Manager).";

  private static final String MESSAGE = "Ticket %d, was escalated via the Escalate button.";

  Ticket ticket;
  HealthCheck health;
  Zendesk zd;
  JSONObject jsonObj;
  String comment;

  Escalate(Ticket ticket, HealthCheck health, Zendesk zd, JSONObject jsonObj) {
    this.ticket = ticket;
    this.health = health;
    this.zd = zd;
    this.jsonObj = jsonObj;
    this.comment = jsonObj.getString("comment");
    health.incrementEscalation();

  }

  void process() {

    if (StringUtils.isEmpty(comment)) {
      LOG.info("{} - No comment field in webhook data", ticket.getId());
      return;  //nothing to do here.
    }

    //remove any leading "-"
    comment = comment.replaceAll("^-+", "");

    LOG.info("{} - payload: {}", ticket.getId(), jsonObj.toString());

    if (!comment.contains(ESCALATION_REQUEST)) {
      if (comment.length() < 200) {
        LOG.info("{} - is not an escalation request \"{}\" ", ticket.getId(), comment);
      } else {
        LOG.info("{} - is not an escalation request \"{}\" ", ticket.getId(), comment.substring(0, 199));
      }
      return;
    }

    // --  check to ensure the ticket is not SOLVED or CLOSED
    if (ticket.getStatus().equals(Status.CLOSED) || ticket.getStatus().equals(Status.SOLVED)) {
      LOG.info("{} - cannot escalate if the ticket is closed or solved.", ticket.getId());
      return;
    }

    if (ticket.getTags().contains(CANNOT_ESCALATE)) {
      LOG.info("{} CANNOT_ESCALATE tag exists ", ticket.getId());
      return;
    }

    // -- support level? only  Gold Support customers can use the Escalate button -- verify this at the org level.
    Organization org = zd.getOrganization(ticket.getOrganizationId());
    if (org.getOrganizationFields().get(ORGANIZATION_SUPPORT_LEVEL) == null || org.getOrganizationFields().get(
            ORGANIZATION_SUPPORT_LEVEL).equals(BRONZE_SUPPORT) || org.getOrganizationFields().get(
            ORGANIZATION_SUPPORT_LEVEL).equals(SILVER_SUPPORT)) {
      List<String> t = ticket.getTags();
      t.add(CANNOT_ESCALATE);
      ticket.setTags(t);
      addTicketComment(ticket, NO_ESCALATION_FOR_BRONZE_SUPPORT);
      LOG.info("{} updated with  ", NO_ESCALATION_FOR_BRONZE_SUPPORT);

      return;
    }

    // get ticket tags - escalation tags.
    List<String> tags = ticket.getTags();
    for (String tag : tags) {
      // check if the ticket has previous escalations.
      if (tag.contains("escalation_")) {
        long tt = Long.parseLong(tag.replace("escalation_", ""));

        if ((System.currentTimeMillis() / 1000) - tt < ESCALATION_INTERVAL) {
          String msg = String.format(TOO_SOON, ((System.currentTimeMillis() / 1000) - tt) / 60);
          addTicketComment(ticket, msg);
          return;
        }
      }
    }

    // got here, then it's a new escalation (or anoother escalation more than ESCALATION_INTERVAL later).

    // delete the previous escalation timestamp tag (not the one for support level)
    List<String> t = ticket.getTags();
    t.removeIf(s -> s.startsWith(ESCALATION_TAG));

    // add a new tag for this escalation.
    String val = ESCALATION_TAG + System.currentTimeMillis() / 1000;
    List<String> t3 = ticket.getTags();
    t3.add(val);
    ticket.setTags(t3);

    // initialize recipient
    String TAMemail = "hari.sankaralingam@dremio.com";
    String TAMname = "Hari Sankaralingam";

    // initialize sender
    String senderEmail = "robertcplotts@gmail.com";
    String senderName = "Bob Plotts";

    // Gather TAM information.  If no TAM, or we fail to lookup the email, send the email to Hari.
    String tmpName = "";
    if (null != org.getOrganizationFields().get("assigned_support_resource")) {
      tmpName = org.getOrganizationFields().get("assigned_support_resource").toString();
    }

    LOG.info("assigned support (TAM) resource: {}", tmpName);
    if (!StringUtils.isEmpty(tmpName)) {
      String tmpEmail = ZendeskUsers.fetchUser(tmpName).getEmail();
      LOG.info("got email {} for TAM resource: {}", tmpEmail, tmpName);

      if (!StringUtils.isEmpty(tmpEmail) && tmpEmail.contains("dremio.com")) {
        TAMemail = tmpEmail;
        TAMname = tmpName;
      }
    }

    String emailSubject = String.format("GOLD ACCOUNT ESCALATION: ticket %d from %s was escalated",
                                        ticket.getId(),
                                        org.getName());
    LOG.info("email subject: {}", emailSubject);

    Long re = ticket.getRequesterId();
    User requester = zd.getUser(re);
    String emailBody = String.format("In ticket %d, %s added this information and requested escalation:  \n\n\n%s",
                                     ticket.getId(),
                                     requester.getName(),
                                     comment);
    LOG.info("email body: {}", emailBody);

    // send the email to notify the TAM or Hari, and cc support leadership.
    SendEmails email = new SendEmails();
    email.send(TAMemail, TAMname, senderEmail, senderName, emailSubject, emailBody);
    LOG.info("email sent to {} {} from {} {}", TAMname, TAMemail, senderName, senderEmail);

    addTicketComment(ticket, ON_CALL_ENGINEER);
    LOG.info("ticket {} from {} updated with: {}", ticket.getId(), org.getName(), ON_CALL_ENGINEER);

  }

  void addTicketComment(Ticket ticket, String comment) {

    Comment c = new Comment();
    c.setId(ticket.getId());
    c.setBody(comment);
    c.setPublic(true);

    // the should probably be a service account.
    User u = ZendeskUsers.fetchUser("Bob Plotts");
    c.setAuthorId(u.getId());

    ticket.setComment(c);
    zd.updateTicket(ticket);
    LOG.info("{} updated with {}", ticket.getId(), comment);

  }
}

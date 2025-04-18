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

import java.util.ArrayList;
import java.util.List;

public class Escalate {
  private static final Logger LOG = LogManager.getLogger(Escalate.class);
  private static final long ESCALATION_INTERVAL = 86400;

  private static final String CANNOT_ESCALATE = "cannot_escalate";

  private static final String ESCALATION_TAG = "escalation_";
  private static final String BRONZE_SUPPORT = "bronze_support";
  private static final String SILVER_SUPPORT = "silver_support";
  private static final String ESCALATION_REQUEST = "[**Escalation Request**]";
  private static final String NOT_ACCEPTED = "Escalation not accepted.  ";
  private static final String TOO_SOON =
          NOT_ACCEPTED + "This ticket was escalated %d minutes ago.  Tickets can be " + "escalated only once per " +
                  "24-hour period.";
  private static final String NO_ESCALATION_FOR_BRONZE_SUPPORT = NOT_ACCEPTED + "Only Gold Support customers are " +
                                                                         "eligible to use the 'Escalate' button.";
  private static final String ON_CALL_ENGINEER = "\n\nThis ticket has been escalated to %s, your TAM (Technical " +
                                                         "Account Manager).\n\n";
  private static final String IS_IT_URGENT =
          "If the issue is URGENT, please open a new ticket with URGENT priority to page the on-call engineer.";

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
      LOG.info("{} - is not an escalation request \"{}\" ",
               ticket.getId(),
               comment.substring(0, Integer.min(comment.length(), 100)));
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

    // -- support level? only Gold Support customers can use the Escalate button -- verify this at the org level.
    Organization org = zd.getOrganization(ticket.getOrganizationId());
    if (org.getOrganizationFields().get(CriticalUpdates.ORGANIZATION_SUPPORT_LEVEL) == null || org.getOrganizationFields().get(
            CriticalUpdates.ORGANIZATION_SUPPORT_LEVEL).equals(BRONZE_SUPPORT) || org.getOrganizationFields().get(
            CriticalUpdates.ORGANIZATION_SUPPORT_LEVEL).equals(SILVER_SUPPORT)) {
      List<String> t = ticket.getTags();
      t.add(CANNOT_ESCALATE);
      ticket.setTags(t);
      addTicketComment(ticket, NO_ESCALATION_FOR_BRONZE_SUPPORT);
      LOG.info("{} updated with  ", NO_ESCALATION_FOR_BRONZE_SUPPORT);

      return;
    }

    // get ticket tags - check the escalation tags.
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

    // got here, then it's a new escalation (or another escalation more than ESCALATION_INTERVAL later).
    health.incrementEscalation();

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

    // TODO: FIX this debugging code.
    String cc = "support-leadership@dremio.com";

    // Gather TAM information.  If no TAM, or we fail to lookup the email, send the email to Hari.
    String tmpName = "";
    if (null != org.getOrganizationFields().get("assigned_support_resource")) {
      tmpName = org.getOrganizationFields().get("assigned_support_resource").toString();
    }

    LOG.info("assigned support (TAM) resource: {}", tmpName);
    if (!StringUtils.isEmpty(tmpName)) {
      String tmpEmail = ZendeskUsers.fetchUser(tmpName).getEmail();
      LOG.info("got email address: {} for TAM resource: {}", tmpEmail, tmpName);

      if (!StringUtils.isEmpty(tmpEmail) && tmpEmail.contains("dremio.com")) {
        TAMemail = tmpEmail;
        TAMname = tmpName;
      }
    }

    String emailSubject = String.format("GOLD ACCOUNT ESCALATION: ticket %d from %s", ticket.getId(), org.getName());
    LOG.info("email subject: {}", emailSubject);

    Long re = ticket.getRequesterId();
    User requester = zd.getUser(re);
    String emailBody = String.format("In ticket %d, %s from %s added this information and requested escalation:  " +
                                             "\n\n%s",
                                     ticket.getId(),
                                     requester.getName(),
                                     org.getName(),
                                     comment);
    LOG.info("email body: {}", emailBody);

    // send the email to notify the TAM or Hari
    List<String> recipients = new ArrayList<>();
    recipients.add(TAMemail);

    // set up CCs to include support-leadership
    List<String> ccs = new ArrayList<>();
    ccs.add("support-leadership@dremio.com");

    SendGmail sg = new SendGmail();
    sg.sendGmail(recipients, ccs, emailSubject, emailBody);
    LOG.info("email sent to {} with cc to {} subject: '{}'", recipients, ccs, emailSubject);

    String ticketComment = String.format(ON_CALL_ENGINEER + IS_IT_URGENT, TAMname);
    addTicketComment(ticket, ticketComment);
    LOG.info("ticket {} from {} updated with: {}", ticket.getId(), org.getName(), comment);

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

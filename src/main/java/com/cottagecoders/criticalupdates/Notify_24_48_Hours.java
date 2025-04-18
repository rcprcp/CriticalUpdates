package com.cottagecoders.criticalupdates;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.Organization;
import org.zendesk.client.v2.model.Ticket;
import org.zendesk.client.v2.model.User;

import java.util.ArrayList;
import java.util.List;

public class Notify_24_48_Hours {
  private static final Logger LOG = LogManager.getLogger(Notify_24_48_Hours.class);
  private static final String URGENT_24 = "24_hour_urgent";
  private static final String URGENT_24_SENT = "24_hour_urgent_sent";
  private static final String HIGH_48 = "48_hour_urgent";
  private static final String HIGH_48_SENT = "48_hour_urgent_sent";
  Ticket ticket = null;
  Zendesk zd = null;
  HealthCheck health = null;

  Notify_24_48_Hours(Ticket ticket, HealthCheck health, Zendesk zd, JSONObject jsonObject) {
    this.ticket = ticket;
    this.zd = zd;
    this.health = health;
  }

  void process() {

    String priority = "";
    int hours = 0;
    List<String> tags = ticket.getTags();

    // check if this ticket contain the indicator that we've done this ticket before:
    if (ticket.getTags().contains(URGENT_24) && !ticket.getTags().contains(URGENT_24_SENT)) {
      tags.add(URGENT_24_SENT);
      priority = "URGENT";
      hours = 24;

    } else if (ticket.getTags().contains(HIGH_48) && !ticket.getTags().contains(HIGH_48_SENT)) {
      tags.add(HIGH_48_SENT);
      priority = "HIGH";
      hours = 48;

    } else {
      LOG.info("{} - does not have the required tags. {} ", ticket.getId(), ticket.getTags());
      return;

    }

    // update ticket here.
    ticket.setTags(tags);
    zd.updateTicket(ticket);

    health.incrementHours24_48();

    if (ticket.getOrganizationId() == null || ticket.getOrganizationId() == 0) {
      LOG.error("{} - organizationId is not valid.", ticket.getId());
      return;
    }

    Organization org = zd.getOrganization(ticket.getOrganizationId());
    String subject = String.format("Ticket %d from %s is priority %s and is %d hours old.",
                                   ticket.getId(),
                                   org.getName(),
                                   priority,
                                   hours);

    String message = String.format("\nTicket %d from %s is priority %s and is %d hours old.",
                                   ticket.getId(),
                                   org.getName(),
                                   priority,
                                   hours);
    message += "\n\nTicket Subject:     " + ticket.getSubject();
    message += "\n\nReported issue:\n\n" + ticket.getDescription();

    List<String> recipients = new ArrayList<>();

    // get account exec name.
    if (org.getOrganizationFields().get(CriticalUpdates.ACCOUNT_EXEC) != null) {
      String ae = org.getOrganizationFields().get(CriticalUpdates.ACCOUNT_EXEC).toString();
      User aeUser = ZendeskUsers.fetchUser(ae);
      recipients.add(aeUser.getEmail());

    } else {
      LOG.error("{} - no {} for {}", ticket.getId(), CriticalUpdates.ACCOUNT_EXEC, org.getName());
      return;
    }

    // get solution architect name.
    if (org.getOrganizationFields().get(CriticalUpdates.SOLUTION_ARCHITECT) != null) {
      String sa = org.getOrganizationFields().get(CriticalUpdates.SOLUTION_ARCHITECT).toString();
      User saUser = ZendeskUsers.fetchUser(sa);
      recipients.add(saUser.getEmail());

    } else {
      LOG.error("{} - no {} for {}", ticket.getId(), CriticalUpdates.SOLUTION_ARCHITECT, org.getName());
      return;
    }

    List<String> ccs = new ArrayList<>();
    ccs.add("support-leadership@dremio.com");
    ccs.add(zd.getUser(ticket.getAssigneeId()).getEmail());

    SendGmail sg = new SendGmail();
    sg.sendGmail(recipients, ccs, subject, message);
  }
}

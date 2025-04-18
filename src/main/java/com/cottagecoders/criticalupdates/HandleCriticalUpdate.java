package com.cottagecoders.criticalupdates;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.Organization;
import org.zendesk.client.v2.model.Ticket;
import org.zendesk.client.v2.model.User;

public class HandleCriticalUpdate {
  private static final Logger LOG = LogManager.getLogger(HandleCriticalUpdate.class);
  private static final String CRITICAL_UPDATE_TAG = "critical_issue_updates";
  private static final  String CRITICAL = "critical";
  Ticket ticket = null;
  Zendesk zd = null;
  HealthCheck health = null;

  HandleCriticalUpdate(Ticket ticket, HealthCheck health, Zendesk zd) {
    this.ticket = ticket;
    this.zd = zd;
    this.health = health;
  }

  void process() {

    // check if this ticket contain the indicator that we've done this ticket before:
    if (ticket.getTags().contains(CRITICAL)) {
      if (!ticket.getTags().contains(CRITICAL_UPDATE_TAG)) {
        health.incrementCriticalUpdates();

        Organization org = zd.getOrganization(ticket.getOrganizationId());

        // fetch the solution architect's user id.
        int update = 0;
        if(org.getOrganizationFields().get(CriticalUpdates.SOLUTION_ARCHITECT) != null) {
          String sa = org.getOrganizationFields().get(CriticalUpdates.SOLUTION_ARCHITECT).toString();
          User saUser = ZendeskUsers.fetchUser(sa);
          ticket.getCollaboratorIds().add(saUser.getId());
          update++;
        }

        // fetch the account exec's user id.
        if(org.getOrganizationFields().get(CriticalUpdates.ACCOUNT_EXEC) != null) {
          String ae = org.getOrganizationFields().get(CriticalUpdates.ACCOUNT_EXEC).toString();
          User aeUser = ZendeskUsers.fetchUser(ae);
          ticket.getCollaboratorIds().add(aeUser.getId());
          update++;
        }

        if(update > 0) {
          zd.updateTicket(ticket);
          LOG.info("{} - added new followers to this ticket.", ticket.getId());

        } else {
          LOG.info("{} - no update applied; no {} or {} ",
                   ticket.getId(),
                   CriticalUpdates.ACCOUNT_EXEC,
                   CriticalUpdates.SOLUTION_ARCHITECT);
        }

      } else {  // not a critical ticket.
        // if the ticket's tags include a CRITICAL_UPDATE_TAG - let's delete the tag.
        if (ticket.getTags().remove(CRITICAL_UPDATE_TAG)) {
          zd.updateTicket(ticket);
        }
      }
    }
  }
}

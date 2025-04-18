package com.cottagecoders.criticalupdates;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.CustomFieldValue;
import org.zendesk.client.v2.model.Organization;
import org.zendesk.client.v2.model.Ticket;

import java.util.Arrays;

// Copy Support Level from Organization to the Ticket
public class SupportLevel {
  private static final Logger LOG = LogManager.getLogger(SupportLevel.class);

  // TODO: need a better lookup for these "custom field id" values.
  // this is the "custom field id" for the per-ticket field that has the support tier in it.
  //private static final Long SUPPORT_TIER_ID = 31905326945691L;    // SANDBOX instance only!!

  // PROD instance only!!
  private static final Long SUPPORT_TIER_ID = 31555045998619L;      // PROD Instance!!

  Ticket ticket;
  HealthCheck health;
  Zendesk zd;
  JSONObject jsonObject;

  SupportLevel(Ticket ticket, HealthCheck health, Zendesk zd, JSONObject jsonObject) {
    this.ticket = ticket;
    this.zd = zd;
    this.health = health;
    this.jsonObject = jsonObject;
  }

  void process() {
    LOG.info("{} - SupportLevel", ticket.getId());
    Organization org = zd.getOrganization(ticket.getOrganizationId());
    if (ticket.getOrganizationId() == null) {
      LOG.info("{} - has a NULL value for getOrganizationId()", ticket.getId());
      return;
    }

    if (org.getOrganizationFields().get(CriticalUpdates.ORGANIZATION_SUPPORT_LEVEL) != null) {    // check for organization's SUPPORT_LEVEL
      LOG.info("{} - support tier {}", ticket.getId(), SUPPORT_TIER_ID);
      for (CustomFieldValue c : ticket.getCustomFields()) {          // get ticket's custom field
        if (c.getId().equals(SUPPORT_TIER_ID)) {// check for matching field id
          LOG.info("{} - matched custom field {}", ticket.getId(), SUPPORT_TIER_ID);
          if (c.getValue() == null || c.getValue().length == 0 || StringUtils.isEmpty(Arrays.toString(c.getValue())) || !c.getValue()[0].equals(
                  org.getOrganizationFields().get(CriticalUpdates.ORGANIZATION_SUPPORT_LEVEL))) {

            health.incrementSupportLevel();

            // set the field's value as String []
            c.setValue(new String[]{((String) org.getOrganizationFields().get(CriticalUpdates.ORGANIZATION_SUPPORT_LEVEL))});
            LOG.info("{} - set Support Tier to {}",
                     ticket.getId(),
                     org.getOrganizationFields().get(CriticalUpdates.ORGANIZATION_SUPPORT_LEVEL));
            zd.updateTicket(ticket);
            health.incrementSupportLevel();
            break;

          }
        }
      }
    }
  }
}

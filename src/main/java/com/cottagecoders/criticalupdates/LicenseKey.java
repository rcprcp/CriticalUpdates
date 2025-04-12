package com.cottagecoders.criticalupdates;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.Status;
import org.zendesk.client.v2.model.Ticket;

public class LicenseKey {

  private static final Logger LOG = LogManager.getLogger(LicenseKey.class);
  private final Ticket ticket;
  private final Zendesk zd;
  private final String comment;
  private static final String LICENSE_REQUEST_TAG = "license_request";
  private static final String LICENSE_STRING = "license";
  private static final String LICENCE_STRING = "licence";

  public LicenseKey(Ticket ticket, HealthCheck health, Zendesk zd, JSONObject jsonObj) {
    health.incrementLicenseKey();
    this.ticket = ticket;
    this.zd = zd;
    this.comment = jsonObj.getString("comment");

  }

  void process() {
    if (! ticket.getStatus().equals(Status.NEW)) {
      LOG.info("{} - skipping this ticket. the status is {} - not NEW", ticket.getId(), ticket.getStatus().toString());
      return;
    }

    if (comment.toLowerCase().contains(LICENSE_STRING)
                || comment.toLowerCase().contains(LICENCE_STRING))  {
      ticket.getTags().add(LICENSE_REQUEST_TAG);
      zd.updateTicket(ticket);
      LOG.info("{} - found '{}' tagging with {}", ticket.getId(), LICENSE_STRING, LICENSE_REQUEST_TAG);

    }
  }

}

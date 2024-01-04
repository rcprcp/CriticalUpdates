package com.cottagecoders.criticalupdates;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.CustomFieldValue;
import org.zendesk.client.v2.model.Field;
import org.zendesk.client.v2.model.Organization;
import org.zendesk.client.v2.model.Ticket;
import org.zendesk.client.v2.model.User;

import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CriticalUpdates {
  static final String ZENDESK_EMAIL = System.getenv("ZENDESK_EMAIL");
  static final String ZENDESK_TOKEN = System.getenv("ZENDESK_TOKEN");
  private static final String ZENDESK_URL = System.getenv("ZENDESK_URL");

  private static final String CRITICAL_UPDATE_TAG = "critical_issue_updates";
  private static final Logger LOG = LogManager.getLogger(CriticalUpdates.class);
  public static HealthCheck health = new HealthCheck();

  public static void main(String... argv) {

    CriticalUpdates main = new CriticalUpdates();
    Zendesk zd = new Zendesk.Builder(ZENDESK_URL).setUsername(ZENDESK_EMAIL).setToken(ZENDESK_TOKEN).build();

    LOG.debug("loading user info");
    ZendeskUsers.init(zd);
    LOG.debug("starting: {}", new Date());

    main.run(zd);
  }

  private void run(Zendesk zd) {

    Javalin app = Javalin.create().start(5002);

    //add routes and methods.
    app.get("/liveness", ctx -> ctx.result("{\"liveness\": \"All good.\"}"));

    app.post("/zendesk/comment", ctx -> {
      process(zd, ctx.body());
      ctx.status(201);
    });

    app.get("/health", ctx -> {
      String ans = "";
      try {
        ObjectMapper mapper = new ObjectMapper();
        ans = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(health);
      } catch (JsonProcessingException ex) {
        System.exit(111);
      }

      ctx.result(ans);
      ctx.status(200);
    });

    app.get("/reloadusers", ctx -> {
      ZendeskUsers.init(zd);
      ctx.result("reloaded users");
      ctx.status(200);
    });

    app.get("/readyness", ctx -> ctx.result("{\"readyness\": \"ready\"}"));
  }


  private void process(Zendesk zd, String incomingJSONString) {

    health.incrementCount();

    // parse incoming payload:
    JSONObject obj = new JSONObject(incomingJSONString);
    long ticketId = obj.getLong("ticketId");

    // get the ticket from Zendesk.
    Ticket ticket = zd.getTicket(ticketId);

    LOG.info("{} Title:  {}", ticket.getId(), ticket.getSubject());

    Map<Long, CustomFieldValue> fieldValues = ticket.getCustomFields().stream().collect(Collectors.toMap(
            CustomFieldValue::getId,
            Function.identity()));
    Map<String, Field> fields = zd.getTicketFields().stream().collect(Collectors.toMap(Field::getTitle,
                                                                                       Function.identity()));
    Long key = fields.get("Critical (cc. all ticket updates to. AE/SA) ").getId();
    CustomFieldValue cfv = fieldValues.get(key);
    if (cfv.getValue() == null || StringUtils.isEmpty(cfv.getValue()[0])) {
      LOG.info("{} Critical Update field isEmpty", ticket.getId());
      return;

    } else if (cfv.getValue()[0].equalsIgnoreCase("false")) {
      LOG.info("{} Critical Update field is false", ticket.getId());
      return;
    }

    // check if this ticket contain the indicator that we've done this ticket before:
    if (!ticket.getTags().contains(CRITICAL_UPDATE_TAG)) {
      health.incrementCriticalUpdates();
      Organization org = zd.getOrganization(ticket.getOrganizationId());

      // fetch the solution architect's user id.
      String sa = org.getOrganizationFields().get("sfdc_solution_architect").toString();
      User saUser = ZendeskUsers.fetchUser(sa);
      ticket.getCollaboratorIds().add(saUser.getId());

      // fetch the account exec's user id.
      String ae = org.getOrganizationFields().get("sfdc_ae").toString();
      User aeUser = ZendeskUsers.fetchUser(ae);
      ticket.getCollaboratorIds().add(aeUser.getId());

      ticket.getTags().add(CRITICAL_UPDATE_TAG);
      zd.updateTicket(ticket);

      LOG.info("{} added {} and new followers: {} ({}) {} ({})",
               CRITICAL_UPDATE_TAG,
               ticket.getId(),
               ae,
               aeUser.getId(),
               sa,
               saUser.getId());

    } else {
      // if the ticket's tags include a CRITICAL_UPDATE_TAG - let's delete the tag.
      if (ticket.getTags().contains(CRITICAL_UPDATE_TAG)) {
        ticket.getTags().remove(CRITICAL_UPDATE_TAG);
        zd.updateTicket(ticket);
        LOG.info("{} NOT Critical, but previously updated. remove {}", ticket.getId(), CRITICAL_UPDATE_TAG);

      }
    }
  }
}

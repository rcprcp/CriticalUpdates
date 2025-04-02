package com.cottagecoders.criticalupdates;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.Ticket;

import java.util.Date;

public class CriticalUpdates {

  private static final Logger LOG = LogManager.getLogger(CriticalUpdates.class);

  static HealthCheck health = new HealthCheck();

  public static void main(String... argv) {

    CriticalUpdates main = new CriticalUpdates();
    Zendesk zd = new Zendesk.Builder(System.getenv("ZENDESK_URL")).setUsername(System.getenv("ZENDESK_EMAIL")).setToken(
            System.getenv("ZENDESK_TOKEN")).build();

    LOG.debug("loading Zendesk user info into memory");
    ZendeskUsers.init(zd);

    LOG.debug("starting: {} environment: {}", new Date(), System.getenv("ZENDESK_URL"));
    main.run(zd);
  }

  private void run(Zendesk zd) {

    Javalin app = Javalin.create().start(5002);

    //add routes and methods.
    app.get("/liveness", ctx -> ctx.result("{\"liveness\": \"All good.\"}"));

    app.post("/zendesk/comment", ctx -> {
      process(zd, ctx.body());
      ctx.result("OK");
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

    app.get("/readyness", ctx -> {
      ctx.result("{\"readyness\": \"ready\"}");
      ctx.status(200);
    });
  }

  private void process(Zendesk zd, String incomingJSONString) {

    health.incrementCount();

    // parse incoming payload:
    JSONObject jsonObj = new JSONObject(incomingJSONString);
    long ticketId = 0;

    // Handle missing ticketId case
    if (jsonObj.has("ticketId")) {
      ticketId = jsonObj.getLong("ticketId");
    } else {
      LOG.error("ticketId is required but missing \"{}\"", incomingJSONString);
      return;
    }

    Ticket ticket = zd.getTicket(ticketId);

    LOG.info("{} Webhook for ticket - Status: {}  Title: {}",
             ticket.getId(),
             ticket.getStatus().toString(),
             ticket.getSubject());

    //-----
    HandleCriticalUpdate update = new HandleCriticalUpdate(ticket, health, zd);
    update.process();

    //-----
    GenerateKB kb = new GenerateKB(ticket, health, zd);
    kb.process();

    //-----
    SupportLevel level = new SupportLevel(ticket, health, zd, jsonObj);
    level.process();

    //-----
    Escalate esc = new Escalate(ticket, health, zd, jsonObj);
    esc.process();

    //-----
    // next thing.

  }


}

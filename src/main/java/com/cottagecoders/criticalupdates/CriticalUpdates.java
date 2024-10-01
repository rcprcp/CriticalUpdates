package com.cottagecoders.criticalupdates;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailjet.client.ClientOptions;
import com.mailjet.client.MailjetClient;
import com.mailjet.client.errors.MailjetException;
import com.mailjet.client.transactional.SendContact;
import com.mailjet.client.transactional.SendEmailsRequest;
import com.mailjet.client.transactional.TransactionalEmail;
import com.mailjet.client.transactional.response.SendEmailsResponse;
import io.javalin.Javalin;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.Comment;
import org.zendesk.client.v2.model.CustomFieldValue;
import org.zendesk.client.v2.model.Organization;
import org.zendesk.client.v2.model.Status;
import org.zendesk.client.v2.model.Ticket;
import org.zendesk.client.v2.model.User;
import org.zendesk.client.v2.model.hc.Article;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CriticalUpdates {

  private static final String CRITICAL_UPDATE_TAG = "critical_issue_updates";
  private static final String CREATE_KB = "kb_generate";
  private static final String KB_CREATED = "kb_created";
  private static final String ADD_ADDITIONAL_CONTENT = "Add additional Content";
  private static final SimpleDateFormat MMMddyyyy = new SimpleDateFormat("MMM/dd/yyyy");
  private static final long troubleshootingSectionID = 4416517577627L;
  private static final long permissionGroup = 4522957825179L;
  private static final long section = 4416528482203L;
  private static final Logger LOG = LogManager.getLogger(CriticalUpdates.class);
  private static final String TEMPLATE = """
          <h2>&#128512; Delete This Section &#128512;</h2>
          <p>
          <a href="%s">Zendesk ticket %d</a>
          </p>
          
          <h2>Summary/Reported Issue</h2>
          <p>
          %s
          </p>
          
          <h2>Relevant Versions</h2>
          <p>
          %s
          </p>
          
          <h2>Troubleshooting Steps</h2>
          <p>
          %s
          </p>
          
          <h2>Cause</h2>
          <p>
          Add additional content
          </p>
          
          <h2>Steps to Resolve</h2>
          <p>
          %s
          </p>
          
          <h2>Next Steps</h2>
          <p>
          Add additional content
          </p>
          
          <h2>Additional Resources</h2>
          <p>
          Add additional content
          </p>
          """;
  static HealthCheck health = new HealthCheck();
  private static MailjetClient client;

  public static void main(String... argv) {

    CriticalUpdates main = new CriticalUpdates();
    Zendesk zd = new Zendesk.Builder(System.getenv("ZENDESK_URL")).setUsername(System.getenv("ZENDESK_EMAIL")).setToken(
            System.getenv("ZENDESK_TOKEN")).build();

    LOG.debug("loading user info");
    ZendeskUsers.init(zd);
    ClientOptions options =
            ClientOptions.builder().apiKey(System.getenv("MJ_APIKEY_PUBLIC")).apiSecretKey(System.getenv(
            "MJ_APIKEY_PRIVATE")).build();

    client = new MailjetClient(options);

    LOG.debug("starting: {}", new Date());
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
    JSONObject obj = new JSONObject(incomingJSONString);
    long ticketId = obj.getLong("ticketId");

    // get the ticket from Zendesk.
    Ticket ticket = zd.getTicket(ticketId);

    LOG.info("{} Webhook for ticket - Title: {} Tags: {}", ticket.getId(), ticket.getSubject(), ticket.getTags());

    // check if this ticket contain the indicator that we've done this ticket before:
    if (ticket.getTags().contains("critical")) {
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

        zd.updateTicket(ticket);

        LOG.info("{} - added new followers: {} ({}) {} ({})", ticket.getId(), ae, aeUser.getId(), sa, saUser.getId());

      } else {  // not a critical ticket.
        // if the ticket's tags include a CRITICAL_UPDATE_TAG - let's delete the tag.
        if (ticket.getTags().remove(CRITICAL_UPDATE_TAG)) {
          zd.updateTicket(ticket);
        }
      }
    }

    //--------------
    // get here to check if we need to generate a KB article (from a closing summary)
    if (ticket.getTags().contains(CREATE_KB) && !ticket.getTags().contains(KB_CREATED)) {
      health.incrementCreateKnowledgeBase();
      LOG.info("{} - Creating KB.", ticket.getId());

      // check for solved or closed only.
      if (!(ticket.getStatus() == Status.SOLVED) && !(ticket.getStatus() == Status.CLOSED)) {
        return;
      }

      // get Dremio version from Ticket's custom fields.
      String version = "Not Specified";
      Iterable<CustomFieldValue> customFields = ticket.getCustomFields();
      for (CustomFieldValue cfv : customFields) {
        if (cfv.getId() == 1900003173044L) {
          version = cfv.getValue()[0];
          version = version.replace("ticket_", "");
          version = version.replace("_", " ");
          version = version.replace("dremio", "Dremio");
          version = version.trim();

          break;
        }
      }

      // scan for a comment containing  "**Summary/Reported Issue**"
      // **Troubleshooting Steps**
      // **Steps to Resolve**
      // **--**
      Iterable<Comment> comments = zd.getRequestComments(ticket.getId());
      for (Comment c : comments) {
        if (c.getBody().contains("**Summary/Reported Issue**")) {
          String summaryReportedIssue = kbParse(c.getBody(), "**Summary/Reported Issue**", "**Troubleshooting Steps**");
          String troubleshootingSteps = kbParse(c.getBody(), "**Troubleshooting Steps**", "**Steps to Resolve**");
          String stepsToResolve = kbParse(c.getBody(), "**Steps to Resolve**", "**--**");

          // create the KB
          Article article = new Article();
          article.setAuthorId(ticket.getAssigneeId());
          article.setSectionId(section);
          List<Long> contentIds = new ArrayList<>();
          article.setContentTagIds(contentIds);

          String title = String.format("%s Draft: %s - component - version: %s",
                                       MMMddyyyy.format(new Date()),
                                       ticket.getSubject(),
                                       version);

          article.setTitle(title);
          String body = String.format(TEMPLATE,
                                      String.format("https://dremio.zendesk.com/agent/tickets/%d", ticket.getId()),
                                      ticket.getId(),
                                      summaryReportedIssue,
                                      version,
                                      troubleshootingSteps,
                                      stepsToResolve);
          article.setBody(body);
          article.setDraft(true);
          article.setSectionId(troubleshootingSectionID);
          article.setPermissionGroupId(permissionGroup);
          article.getContentTagIds();
          article.setHtmlUrl("");
          article.setUrl("");
          zd.createArticle(article);
          LOG.info("{} - Created article.", ticket.getId());

          List<String> tags = ticket.getTags();
          tags.add(KB_CREATED);
          ticket.setTags(tags);
          LOG.info("[] - about set ticket tags to {}" + ticket.getId(), tags);
          zd.updateTicket(ticket);

          String emailTemplate = """
                  <p>
                  Based on the "Generate a KB" option on support ticket %d, a KB was just created with a title matching this email.
                  Please revise the content in the KB and publish it.  Otherwise, if the KB is not necessary, please delete it.
                  <p>
                  Thank you!
                  <hr>
                  """;

          String emailBody = String.format(emailTemplate, ticket.getId());

          // send an email to the support engineer.
          User recipient = zd.getUser(ticket.getAssigneeId());
          TransactionalEmail email = TransactionalEmail.builder().to(new SendContact(recipient.getEmail(),
                                                                                     recipient.getName())).from(new SendContact(
                          "robertcplotts@gmail.com",
                          "bplotts")).subject(String.format("%s", title)).htmlPart(emailBody)
//                                                .trackOpens(TrackOpens.ENABLED)
//                                                .attachment(Attachment.fromFile(attachmentPath))
//                                                .header("test-header-key", "test-value")
//                                                .customID("custom-id-value")
                                             .build();
          LOG.info("{} - created email to {}", ticket.getId(), recipient.getEmail());
          SendEmailsRequest request = SendEmailsRequest.builder().message(email) // you can add up to 50 messages per
                                              // request
                                              .build();

          email = TransactionalEmail.builder().to(new SendContact("bob@dremio.com", "bob")).from(new SendContact(
                          "robertcplotts@gmail.com",
                          "Bob Plotts")).subject(String.format("%s", title)).htmlPart(emailBody)
//                                                .trackOpens(TrackOpens.ENABLED)
//                                                .attachment(Attachment.fromFile(attachmentPath))
//                                                .header("test-header-key", "test-value")
//                                                .customID("custom-id-value")
                          .build();
          LOG.info("{} - created email to {}", ticket.getId(), "bob@dremio.com");
          request = SendEmailsRequest.builder().message(email) // you can add up to 50 messages per request
                            .build();
          try {
            LOG.info("{} - sending email batch", ticket.getId());
            SendEmailsResponse response = request.sendWith(client);

          } catch (MailjetException ex) {
            LOG.error("{} Exception: {}", ticket.getId(), ex.getMessage(), ex);
          }
          break;
        }
      }
    }
  }

  String kbParse(String body, String section, String endOfSection) {
    int start = body.indexOf(section);
    if (start == -1) {
      return ADD_ADDITIONAL_CONTENT;

    }

    start += section.length();
    body = body.substring(start);

    int end = body.indexOf(endOfSection);
    if (end < 0) {
      end = body.length();

    }

    if (StringUtils.isEmpty(body.substring(0, end))) {
      return ADD_ADDITIONAL_CONTENT;

    } else {
      body = body.substring(0, end);
      body = body.trim();
      body = StringUtils.stripStart(body, "*");
      body = StringUtils.stripEnd(body, "*");
      body = body.replace("\n\n", "<br/>");
      body = body.trim();
      return body;

    }
  }
}

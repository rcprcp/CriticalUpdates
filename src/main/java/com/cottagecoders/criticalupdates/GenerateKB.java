package com.cottagecoders.criticalupdates;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.Comment;
import org.zendesk.client.v2.model.CustomFieldValue;
import org.zendesk.client.v2.model.Status;
import org.zendesk.client.v2.model.Ticket;
import org.zendesk.client.v2.model.User;
import org.zendesk.client.v2.model.hc.Article;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GenerateKB {
  private static final Logger LOG = LogManager.getLogger(GenerateKB.class);

  // these are constants defined in Zendesk for the Dremio Org.
  private static final long section = 4416528482203L;
  private static final long troubleshootingSectionID = 4416517577627L;
  private static final long permissionGroup = 4522957825179L;

  private static final String CREATE_KB = "kb_generate";
  private static final String KB_CREATED = "kb_created";
  private static final String ADD_ADDITIONAL_CONTENT = "Add additional Content";
  private static final SimpleDateFormat MMMddyyyy = new SimpleDateFormat("MMM/dd/yyyy");
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
  Ticket ticket = null;
  HealthCheck health = null;
  Zendesk zd = null;

  public GenerateKB(Ticket ticket, HealthCheck health, Zendesk zd) {
    this.ticket = ticket;
    this.health = health;
    health.incrementCreateKnowledgeBase();
    this.zd = zd;
  }

  void process() {
    // get here to check if we need to generate a KB article (from a closing summary)
    if (ticket.getTags().contains(CREATE_KB) && !ticket.getTags().contains(KB_CREATED)) {
      health.incrementCreateKnowledgeBase();
      LOG.info("{} - Creating KB.", ticket.getId());

      // check for solved or closed only.
      if (!(ticket.getStatus() == Status.SOLVED) && !(ticket.getStatus() == Status.CLOSED)) {
        LOG.info("{} not solved or closed.", ticket.getId());
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

      // scan for a comment containing:
      // "**Summary/Reported Issue**"
      // **Troubleshooting Steps**
      // **Steps to Resolve**
      // **--**
      LOG.info("{} - start iterating comments..", ticket.getId());
      Iterable<Comment> comments = zd.getRequestComments(ticket.getId());
      for (Comment c : comments) {
        if (c.getBody().contains("**Summary/Reported Issue**")) {
          String summaryReportedIssue = kbParse(c.getBody(), "**Summary/Reported Issue**", "**Troubleshooting Steps**");
          String troubleshootingSteps = kbParse(c.getBody(), "**Troubleshooting Steps**", "**Steps to Resolve**");
          String stepsToResolve = kbParse(c.getBody(), "**Steps to Resolve**", "**--**");

          Article article = new Article();

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

          List<String> contentIds = new ArrayList<>();
          article.setContentTagIds(contentIds);
          article.setAuthorId(ticket.getAssigneeId());
          article.setSectionId(section);
          article.setDraft(true);
          article.setSectionId(troubleshootingSectionID);
          article.setPermissionGroupId(permissionGroup);
          article.getContentTagIds();
          article.setHtmlUrl("");
          article.setUrl("");
          LOG.info("{} - Creating article.", ticket.getId());
          zd.createArticle(article);


          List<String> tags = ticket.getTags();
          tags.add(KB_CREATED);
          ticket.setTags(tags);
          LOG.info("{} - about set ticket tags to {}", ticket.getId(), tags);
          zd.updateTicket(ticket);

          String emailTemplate = """
                  \n
                  \n
                  Based on the "Generate a KB" option on support ticket %d, a KB was just created with a title matching this email.
                  Please revise the content in the KB and publish it.  Otherwise, if the KB is not necessary, please delete it.
                  \n
                  
                  Thank you!
                  """;

          String emailBody = String.format(emailTemplate, ticket.getId());

          // send an email to the support engineer.
          User recipient = zd.getUser(ticket.getAssigneeId());

          LOG.info("{} - created email to {}", ticket.getId(), recipient.getEmail());
          SendGmail sg = new SendGmail();
          sg.sendGmail(recipient.getEmail(), "support-leadership@dremio.com", title, emailBody);
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
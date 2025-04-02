package com.cottagecoders.criticalupdates;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class PagerDuty {
  private static final Logger LOG = LogManager.getLogger(PagerDuty.class);
  private static final String PD_ROUTING_KEY = System.getenv("PD_ROUTING_KEY")  ;


  public PagerDuty() {
    // nothing
  }

  public static void sendAlert(String summary) {

    String source = "CriticalUpdates";
    String severity = "critical";
    try {
      HttpClient client = HttpClient.newHttpClient();

      String jsonPayload = String.format("""
                                                     {
                                                     "payload": {
                                                         "summary": "%s",
                                                         "severity": "%s",
                                                         "source": "%s"
                                                     },
                                                     "routing_key": "%s",
                                                     "event_action": "trigger"
                                                 }""",
                                         summary, severity, source, PD_ROUTING_KEY);

      HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://events.pagerduty.com/v2/enqueue")).header(
              "Content-Type",
              "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonPayload)).build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      LOG.info("PagerDuty statusCode: {} Response Body {}", response.statusCode(), response.body());

    } catch (Exception ex) {
      LOG.error("Exception sending to PagerDuty: {}", ex.getMessage(), ex);
    }
  }
}
package com.cottagecoders.criticalupdates;

public class HealthCheck {

  // These have to be declared as public so that the JSON builder can find these variables
  // to create the JSON payload.
  public long startEpoch;
  public long lastWebhookEpoch;
  public long webhookCount;
  public long criticalUpdates;
  public long createKnowledgeBase;
  public long escalate;
  public long hours_24_48;
  public long licenseKey;

  public long supportLevel;
  public long totalMemoryMB;
  public long freeMemoryMB;

  public HealthCheck() {
    startEpoch = System.currentTimeMillis();
    lastWebhookEpoch = 0;
    webhookCount = 0;
    criticalUpdates = 0;
    createKnowledgeBase = 0;
    escalate = 0;
    supportLevel = 0;
    licenseKey =0;

    totalMemoryMB = Runtime.getRuntime().totalMemory() / 1024 / 1024;
    freeMemoryMB = Runtime.getRuntime().freeMemory() / 1024 / 1024;
  }

  public long getLastWebhookEpoch() {
    return lastWebhookEpoch;
  }

  public long getWebhookCount() {
    return webhookCount;
  }

  public long getCriticalUpdates() {
    return criticalUpdates;
  }

  public long getCreateKnowledgeBase() {
    return createKnowledgeBase;
  }

  public long getFreeMemoryMB() {
    return freeMemoryMB;
  }

  public void incrementCount() {
    webhookCount++;
    lastWebhookEpoch = System.currentTimeMillis();
  }

  public void incrementCriticalUpdates() {
    criticalUpdates++;
  }

  public void incrementCreateKnowledgeBase() {
    createKnowledgeBase++;
  }

  public void incrementEscalation() {
    escalate++;
  }

  public void incrementSupportLevel() {
    supportLevel++;
  }

  public void incrementHours24_48() {
    hours_24_48++;
  }

  public void incrementLicenseKey() {
    licenseKey++;
  }
}

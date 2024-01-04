package com.cottagecoders.criticalupdates;

public class HealthCheck {

  public long startEpoch;
  public long lastWebhookEpoch;
  public long webhookCount;
  public long criticalUpdates;
  public long totalMemoryMB;
  public long freeMemoryMB;

  public HealthCheck() {
    startEpoch = System.currentTimeMillis();
    lastWebhookEpoch = 0;
    webhookCount = 0;
    criticalUpdates = 0;
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
}

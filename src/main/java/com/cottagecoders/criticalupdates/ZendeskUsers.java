package com.cottagecoders.criticalupdates;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.User;

import java.util.HashMap;
import java.util.Map;

public class ZendeskUsers {
  private static final Logger LOG = LogManager.getLogger(CriticalUpdates.class);
  private static final Map<String, User> users = new HashMap<>();

  static void init(Zendesk zd) {
    // initialize the Zendesk Users
    int count = 0;
    for (User u : zd.getUsers()) {
      users.put(u.getName(), u);
      count++;
    }
    LOG.info("fetched {} user records", count);

  }

  static User fetchUser(String name) {
    return users.get(name);

  }
}

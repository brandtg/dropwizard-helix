package com.github.brandtg.discovery;

import org.apache.helix.NotificationContext;
import org.apache.helix.api.TransitionHandler;
import org.apache.helix.model.Message;
import org.apache.helix.participant.statemachine.StateModelInfo;
import org.apache.helix.participant.statemachine.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@StateModelInfo(states = "{'OFFLINE','ONLINE'}", initialState = "OFFINE")
public class HelixServiceDiscoveryStateTransitionHandler extends TransitionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(HelixServiceDiscoveryStateTransitionHandler.class);

  @Transition(from = "OFFLINE", to = "ONLINE")
  public void fromOfflineToOnline(Message message, NotificationContext context) {
    LOG.info("From OFFLINE to ONLINE: {}", message.getPartitionName());
  }

  @Transition(from = "ONLINE", to = "OFFLINE")
  public void fromOnlineToOffline(Message message, NotificationContext context) {
    LOG.info("From ONLINE to OFFLINE: {}", message.getPartitionName());
  }
}

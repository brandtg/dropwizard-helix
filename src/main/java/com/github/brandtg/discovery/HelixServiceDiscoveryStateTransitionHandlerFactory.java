package com.github.brandtg.discovery;

import org.apache.helix.api.StateTransitionHandlerFactory;
import org.apache.helix.api.id.PartitionId;

public class HelixServiceDiscoveryStateTransitionHandlerFactory
    extends StateTransitionHandlerFactory<HelixServiceDiscoveryStateTransitionHandler> {
  @Override
  public HelixServiceDiscoveryStateTransitionHandler createStateTransitionHandler(PartitionId partitionId) {
    return new HelixServiceDiscoveryStateTransitionHandler();
  }
}

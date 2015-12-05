/**
 * Copyright (C) 2015 Greg Brandt (brandt.greg@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

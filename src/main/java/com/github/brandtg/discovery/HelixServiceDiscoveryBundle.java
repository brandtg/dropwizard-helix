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

import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.helix.HelixAdmin;
import org.apache.helix.HelixConnection;
import org.apache.helix.HelixParticipant;
import org.apache.helix.api.id.ClusterId;
import org.apache.helix.api.id.ParticipantId;
import org.apache.helix.api.id.StateModelDefId;
import org.apache.helix.manager.zk.ZkHelixConnection;
import org.apache.helix.model.InstanceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public abstract class HelixServiceDiscoveryBundle<T extends Configuration> implements ConfiguredBundle<T> {
  private static final Logger LOG = LoggerFactory.getLogger(HelixServiceDiscoveryBundle.class);

  @Override
  public void initialize(Bootstrap<?> bootstrap) {
    // NOP
  }

  @Override
  public void run(final T configuration, Environment environment) throws Exception {
    final HelixServiceDiscoveryConfiguration helixConfig
        = getHelixServiceDiscoveryBundleConfiguration(configuration);
    final HelixConnection helixConnection
        = new ZkHelixConnection(helixConfig.getZkAddress());
    final AtomicReference<HelixParticipant> helixParticipantReference
        = new AtomicReference<>();

    environment.lifecycle().manage(new Managed() {
      @Override
      public void start() throws Exception {
        String hostName = InetAddress.getLocalHost().getCanonicalHostName();
        int port = getPort(configuration);
        String instanceName = String.format("%s_%d", hostName, port);

        // Connect to Helix
        helixConnection.connect();
        HelixParticipant helixParticipant = helixConnection.createParticipant(
            ClusterId.from(helixConfig.getClusterName()),
            ParticipantId.from(instanceName));

        // Add this node if not present to cluster
        HelixAdmin helixAdmin = helixConnection.createClusterManagementTool();
        List<String> nodes = helixAdmin.getInstancesInCluster(helixConfig.getClusterName());
        if (!nodes.contains(instanceName)) {
          InstanceConfig instanceConfig = new InstanceConfig(instanceName);
          instanceConfig.setHostName(hostName);
          instanceConfig.setPort(String.valueOf(port));
          helixAdmin.addInstance(helixConfig.getClusterName(), instanceConfig);
          LOG.info("Added instance {} to cluster {}", instanceName, helixConfig.getClusterName());
        }

        // Register the online / offline state machine
        helixParticipant.getStateMachineEngine().registerStateModelFactory(
            StateModelDefId.OnlineOffline,
            new HelixServiceDiscoveryStateTransitionHandlerFactory());

        // Start participant
        helixParticipant.start();
        helixParticipantReference.set(helixParticipant);
        LOG.info("Connected to {}/{} as {}",
            helixConfig.getZkAddress(),
            helixConfig.getClusterName(),
            instanceName);
      }

      @Override
      public void stop() throws Exception {
        // Stop participant
        HelixParticipant helixParticipant = helixParticipantReference.get();
        if (helixParticipant != null) {
          helixParticipant.stop();
        }

        // Disconnect from Helix
        helixConnection.disconnect();
        LOG.info("Disconnected from {}/{}",
            helixConfig.getZkAddress(),
            helixConfig.getClusterName());
      }
    });
  }

  protected abstract HelixServiceDiscoveryConfiguration
  getHelixServiceDiscoveryBundleConfiguration(T configuration);

  private static int getPort(Configuration config) {
    DefaultServerFactory serverFactory = (DefaultServerFactory) config.getServerFactory();
    ConnectorFactory connectorFactory = serverFactory.getApplicationConnectors().get(0);

    if (connectorFactory instanceof HttpsConnectorFactory) {
      return ((HttpsConnectorFactory) connectorFactory).getPort();
    } else if (connectorFactory instanceof HttpConnectorFactory) {
      return ((HttpConnectorFactory) connectorFactory).getPort();
    }

    throw new IllegalArgumentException("Could not extract main application port from configuration");
  }
}

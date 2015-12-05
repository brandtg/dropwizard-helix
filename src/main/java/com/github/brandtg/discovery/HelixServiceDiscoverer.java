package com.github.brandtg.discovery;

import io.dropwizard.lifecycle.Managed;
import org.apache.helix.HelixAdmin;
import org.apache.helix.HelixRole;
import org.apache.helix.LiveInstanceChangeListener;
import org.apache.helix.NotificationContext;
import org.apache.helix.api.id.ClusterId;
import org.apache.helix.api.id.SpectatorId;
import org.apache.helix.manager.zk.ZkHelixConnection;
import org.apache.helix.manager.zk.ZkHelixRoleDefaultImpl;
import org.apache.helix.model.InstanceConfig;
import org.apache.helix.model.LiveInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class HelixServiceDiscoverer implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(HelixServiceDiscoverer.class);
  private static final Random RANDOM = new Random();

  private final AtomicBoolean isStarted;
  private final String zkAddress;
  private final String clusterName;
  private final ZkHelixConnection helixConnection;
  private final AtomicReference<List<InetSocketAddress>> services;

  public HelixServiceDiscoverer(String zkAddress, String clusterName) {
    this.isStarted = new AtomicBoolean();
    this.zkAddress = zkAddress;
    this.clusterName = clusterName;
    this.helixConnection = new ZkHelixConnection(zkAddress);
    this.services = new AtomicReference<>();
  }

  @Override
  public void start() throws Exception {
    if (!isStarted.getAndSet(true)) {
      helixConnection.connect();

      ClusterId clusterId = ClusterId.from(clusterName);
      SpectatorId spectatorId = SpectatorId.from(HelixServiceDiscoverer.class.getSimpleName());
      HelixRole spectatorRole = new ZkHelixRoleDefaultImpl(helixConnection, clusterId, spectatorId);

      helixConnection.addLiveInstanceChangeListener(
          spectatorRole,
          new ServiceDiscoveryLiveInstanceChangeListener(),
          clusterId);
    }
  }

  @Override
  public void stop() throws Exception {
    if (isStarted.getAndSet(false)) {
      helixConnection.disconnect();
    }
  }

  private class ServiceDiscoveryLiveInstanceChangeListener implements LiveInstanceChangeListener {
    @Override
    public void onLiveInstanceChange(List<LiveInstance> list, NotificationContext notificationContext) {
      List<InetSocketAddress> addresses = new ArrayList<>();
      HelixAdmin helixAdmin = helixConnection.createClusterManagementTool();
      for (LiveInstance liveInstance : list) {
        InstanceConfig instanceConfig = helixAdmin.getInstanceConfig(
            clusterName,
            liveInstance.getInstanceName());
        InetSocketAddress address = new InetSocketAddress(
            instanceConfig.getHostName(),
            Integer.valueOf(instanceConfig.getPort()));
        addresses.add(address);
      }

      services.set(addresses);
    }
  }

  public List<InetSocketAddress> getServices() {
    return services.get();
  }

  public InetSocketAddress getRandomService() {
    List<InetSocketAddress> addresses = services.get();
    if (addresses == null || addresses.isEmpty()) {
      throw new NoSuchElementException();
    }

    int serviceIdx = RANDOM.nextInt(addresses.size());
    return addresses.get(serviceIdx);
  }
}

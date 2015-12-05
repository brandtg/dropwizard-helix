package com.github.brandtg.discovery;

public class HelixServiceDiscoveryConfiguration {
  private String zkAddress;
  private String clusterName;

  public HelixServiceDiscoveryConfiguration() {}

  public String getZkAddress() {
    return zkAddress;
  }

  public void setZkAddress(String zkAddress) {
    this.zkAddress = zkAddress;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }
}

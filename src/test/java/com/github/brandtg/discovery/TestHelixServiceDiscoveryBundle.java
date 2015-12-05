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

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import junit.framework.Assert;
import org.I0Itec.zkclient.IDefaultNameSpace;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.ZkServer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.helix.HelixManager;
import org.apache.helix.controller.HelixControllerMain;
import org.apache.helix.tools.ClusterSetup;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class TestHelixServiceDiscoveryBundle {
  // Server config
  public static class SimpleServerConfiguration extends Configuration {
    private HelixServiceDiscoveryConfiguration helix;

    public HelixServiceDiscoveryConfiguration getHelix() {
      return helix;
    }

    public void setHelix(HelixServiceDiscoveryConfiguration helix) {
      this.helix = helix;
    }
  }

  // A Hello World resource
  @Path("/hello-world")
  public static class HelloWorldResource {
    @GET
    public Response sayHello() {
      return Response.ok("Hello World!").build();
    }
  }

  // A simple Dropwizard server with the Helix service discovery bundle
  public static class SimpleServer extends Application<SimpleServerConfiguration> {
    public SimpleServer() {}

    @Override
    public void initialize(Bootstrap<SimpleServerConfiguration> bootstrap) {
      bootstrap.addBundle(new HelixServiceDiscoveryBundle<SimpleServerConfiguration>() {
        @Override
        protected HelixServiceDiscoveryConfiguration
        getHelixServiceDiscoveryBundleConfiguration(SimpleServerConfiguration configuration) {
          return configuration.getHelix();
        }
      });
    }

    @Override
    public void run(SimpleServerConfiguration configuration, Environment environment) throws Exception {
      environment.jersey().register(new HelloWorldResource());
    }
  }

  // Constants
  private int zkPort = 50000;
  private String zkAddress = String.format("localhost:%d", zkPort);
  private String clusterName = "TEST_CLUSTER";
  private int baseServerPort = 30000;
  private int baseAdminPort = 40000;

  private File zkRoot;
  private ZkServer zkServer;
  private HelixManager helixController;
  private HelixServiceDiscoverer serviceDiscoverer;

  @BeforeClass
  public void beforeClass() {
    // Start ZK
    zkRoot = new File(System.getProperty("java.io.tmpdir"), TestHelixServiceDiscoveryBundle.class.getSimpleName());
    zkServer = new ZkServer(new File(zkRoot, "data").getAbsolutePath(),
        new File(zkRoot, "log").getAbsolutePath(),
        new IDefaultNameSpace() {
          @Override
          public void createDefaultNameSpace(ZkClient zkClient) {
            // NOP
          }
        }, zkPort);
    zkServer.start();
  }

  @AfterClass
  public void afterClass() throws Exception {
    zkServer.shutdown();
    FileUtils.forceDelete(zkRoot);
  }

  @BeforeMethod
  public void beforeMethod() throws Exception {
    // Set up helix cluster
    ClusterSetup clusterSetup = new ClusterSetup(zkAddress);
    clusterSetup.addCluster(clusterName, true);

    // Start Helix controller
    helixController = HelixControllerMain.startHelixController(zkAddress, clusterName, "CONTROLLER", "STANDALONE");

    // Service discoverer
    serviceDiscoverer = new HelixServiceDiscoverer(zkAddress, clusterName);
    serviceDiscoverer.start();
  }

  @AfterMethod
  public void afterMethod() throws Exception {
    // Stop service discoverer
    serviceDiscoverer.stop();

    // Stop Helix controller
    helixController.disconnect();
  }

  @Test
  public void testServiceDiscovery() throws Exception {
    int numServices = 3;

    // Start some servers
    List<DropWizardApplicationRunner.DropWizardServer<SimpleServerConfiguration>> servers = new ArrayList<>();
    for (int i = 0; i < numServices; i++) {
      int serverPort = baseServerPort + i;
      int adminPort = baseAdminPort + i;

      HelixServiceDiscoveryConfiguration helixConfig = new HelixServiceDiscoveryConfiguration();
      helixConfig.setZkAddress(zkAddress);
      helixConfig.setClusterName(clusterName);

      SimpleServerConfiguration config = new SimpleServerConfiguration();
      config.setHelix(helixConfig);
      setPort(config, serverPort);
      setAdminPort(config, adminPort);

      DropWizardApplicationRunner.DropWizardServer<SimpleServerConfiguration> server =
          DropWizardApplicationRunner.createServer(config, SimpleServer.class);

      server.start();
      servers.add(server);
    }

    // Check that we can sayHello on each
    waitForServices(serviceDiscoverer, numServices);
    List<InetSocketAddress> services = serviceDiscoverer.getServices();
    Assert.assertEquals(services.size(), numServices);
    checkServices(services);

    // Disable one, and ensure that we have one less
    servers.get(0).stop();
    waitForServices(serviceDiscoverer, numServices - 1);
    services = serviceDiscoverer.getServices();
    Assert.assertEquals(services.size(), numServices - 1);
    checkServices(services);
  }

  private static void setPort(Configuration config, int port) {
    DefaultServerFactory serverFactory = (DefaultServerFactory) config.getServerFactory();
    HttpConnectorFactory connectorFactory = (HttpConnectorFactory) serverFactory.getApplicationConnectors().get(0);
    connectorFactory.setPort(port);
  }

  private static void setAdminPort(Configuration config, int port) {
    DefaultServerFactory serverFactory = (DefaultServerFactory) config.getServerFactory();
    HttpConnectorFactory connectorFactory = (HttpConnectorFactory) serverFactory.getAdminConnectors().get(0);
    connectorFactory.setPort(port);
  }

  private static void waitForServices(HelixServiceDiscoverer serviceDiscoverer, int expectedInstances) {
    long startTimeMillis = System.currentTimeMillis();
    do {
      List<InetSocketAddress> services = serviceDiscoverer.getServices();
      if (services != null && services.size() == expectedInstances) {
        return;
      }
    } while (System.currentTimeMillis() - startTimeMillis < 10000);

    Assert.fail("Timed out while waiting for services to become " + expectedInstances);
  }

  private static void checkServices(List<InetSocketAddress> services) throws Exception {
    for (InetSocketAddress service : services) {
      HttpURLConnection conn = (HttpURLConnection) new URL(
          String.format("http://%s:%d/hello-world", service.getHostName(), service.getPort())).openConnection();
      String result = IOUtils.toString(conn.getInputStream());
      Assert.assertEquals(result, "Hello World!");
    }
  }
}

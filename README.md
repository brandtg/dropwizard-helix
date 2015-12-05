dropwizard-helix
================

This project integrates [Dropwizard](http://www.dropwizard.io/), a Java web service framework,
with [Apache Helix](http://helix.apache.org/), a cluster management framework for partitioned and replicated
distributed resources.

Service Discovery
-----------------

A very simple use case is service discovery. In Helix terms, we model each service as a Cluster, and
services are Instances within that cluster.

First build and install install into your local repo:

```
mvn install
```

Then add the dependency in your project

```
<dependency>
    <groupId>com.github.brandtg</groupId>
    <artifactId>dropwizard-helix</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

_(TODO: Publish artifact to maven central, then put dependency snippet here)_

To enable a Dropwizard application with service discovery, add the following to your configuration class:

```java
public class MyConfiguration extends Configuration {
    private HelixServiceDiscoveryConfiguration helix;
    
    public HelixServiceDiscoveryConfiguration getHelix() {
        return helix;
    }
}
```

Next, add the bundle in your Application's `initialize` method, e.g.:


```java
public static class SimpleServer extends Application<SimpleServerConfiguration> {

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
    
    ...
}
```


Finally, add the following to your server config (substituting your zkAddress and clusterName):

```
helix:
    zkAddress: 'localhost:2181'
    clusterName: 'MY_SERVICE'
```

The Helix Instance name is derived from the host name the machine is running on, and the first Dropwizard
application connector. E.g. `myhost-123.mycompany.com_10000`

In your client code, you can use `HelixServiceDiscoverer` to find the application socket addresses:

```java
// Create and start discovery class (connect to ZK)
HelixServiceDiscoverer discoverer = new HelixServiceDiscoverer("localhost:2181", "MY_SERVICE");
discoverer.start();

// Get all services
List<InetSocketAddress> services = discoverer.getServices();

// Get a random service
InetSocketAddress service = discoverer.getService();

// Disconnect from ZK
discoverer.stop();
```

This class implements `Managed`, so be sure to add the following in your application's lifecycle. If this is
done, `#start` and `#stop` methods do not need to be called.

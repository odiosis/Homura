package top.devgo.vertx.server;

import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.hazelcast.config.Config;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.Match;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import java.util.concurrent.TimeUnit;

public class Bootstrap {

    public static void main(String[] args) {
        System.setProperty("vertx.logger-delegate-factory-class-name", SLF4JLogDelegateFactory.class.getCanonicalName());

        Config config = new Config();
        config.setInstanceName("Homura");
        config.getGroupConfig().setName("homura-hazel-cluster");

        VertxOptions options = new VertxOptions()
                .setPreferNativeTransport(true)
                .setClusterManager(new HazelcastClusterManager(config))
                .setMetricsOptions(new DropwizardMetricsOptions().setEnabled(true).setRegistryName("chaser").addMonitoredEventBusHandler(new Match().setValue("group_talk")));

        Vertx.clusteredVertx(options, res -> {
            if (res.succeeded()) {
                Vertx vertx = res.result();

                int cores = Runtime.getRuntime().availableProcessors();
                System.out.println("native transport enabled: " + vertx.isNativeTransportEnabled());

                vertx.deployVerticle(Server.class, new DeploymentOptions().setInstances(cores));
                vertx.deployVerticle(EventProcessor.class.getCanonicalName());
                vertx.deployVerticle(EventProcessorRx.class.getCanonicalName());


                GraphiteReporter.forRegistry(SharedMetricRegistries.getOrCreate("chaser"))
                        .prefixedWith("Homura")
                        .build(new Graphite("172.16.6.125",2003)).start(10, TimeUnit.SECONDS);
            }
        });

    }
}

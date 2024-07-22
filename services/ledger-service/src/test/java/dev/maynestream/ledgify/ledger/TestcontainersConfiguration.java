package dev.maynestream.ledgify.ledger;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import dev.maynestream.ledgify.ledger.bookkeeper.BookkeeperContainer;
import dev.maynestream.ledgify.ledger.bookkeeper.ZookeeperContainer;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.testcontainers.containers.Network;

import static dev.maynestream.ledgify.ledger.bookkeeper.ZookeeperContainer.ZOOKEEPER_HOST;
import static dev.maynestream.ledgify.ledger.bookkeeper.ZookeeperContainer.ZOOKEEPER_PORT;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    private static final Network NETWORK = Network.newNetwork();

    @Bean
    @RestartScope
    @SuppressWarnings("deprecation")
    public ZookeeperContainer zookeeperContainer() {
        //noinspection resource
        return new ZookeeperContainer()
                .withNetwork(NETWORK)
                // required workaround to let host communicate with container network
                // see https://stackoverflow.com/a/71854622/1165078
                .withCreateContainerCmdModifier(cmd -> cmd.withNetworkMode(NETWORK.getId()).withHostConfig(
                                                                  new HostConfig()
                                                                          .withPortBindings(new PortBinding(
                                                                                  Ports.Binding.bindPort(ZOOKEEPER_PORT), new ExposedPort(ZOOKEEPER_PORT))))
                                                          .withNetworkMode(NETWORK.getId()));
    }

    @Bean
    @RestartScope
    @DependsOn("zookeeperContainer")
    public BookkeeperContainer bookkeeperContainer() {
        //noinspection resource
        return new BookkeeperContainer()
                .withNetwork(NETWORK)
                .withEnv("BK_zkServers", ZOOKEEPER_HOST + ":" + ZOOKEEPER_PORT);
    }
}

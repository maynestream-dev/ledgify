package dev.maynestream.ledgify.ledger;

import dev.maynestream.ledgify.ledger.bookkeeper.BookkeeperContainer;
import dev.maynestream.ledgify.ledger.bookkeeper.ZookeeperContainer;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    private static final Network NETWORK = Network.newNetwork();
    private static final int ZOOKEEPER_CLUSTER_SIZE = 3;
    private static final int BOOKKEEPER_CLUSTER_SIZE = 1;
    private static final String LOCALHOST = "localhost";

    @Bean
    @RestartScope
    public List<ZookeeperContainer> zookeeperCluster(DynamicPropertyRegistry dynamicPropertyRegistry) {
        final List<ZookeeperContainer> zookeeperContainers = startZookeeperCluster();
        setZookeeperEnvironmentProperties(dynamicPropertyRegistry, zookeeperContainers);
        return zookeeperContainers;
    }

    @Bean
    @RestartScope
    public List<BookkeeperContainer> bookkeeperCluster(Collection<ZookeeperContainer> zookeeperContainers) {
        return startBookkeeperCluster(zookeeperContainers);
    }

    private static void setZookeeperEnvironmentProperties(final DynamicPropertyRegistry dynamicPropertyRegistry, final List<ZookeeperContainer> zookeeperContainers) {
        final Set<String> zookeeperHosts = getZookeeperHosts(zookeeperContainers);

        dynamicPropertyRegistry.add("ledger.bookkeeper.zk-servers",
                                    () -> String.join(",", zookeeperHosts));
        dynamicPropertyRegistry.add("ledger.bookkeeper.metadata-service-uri",
                                    () -> "zk+hierarchical://" + String.join(";", zookeeperHosts) + "/ledgers");
    }

    private static Set<String> getZookeeperHosts(final List<ZookeeperContainer> zookeeperContainers) {
        return zookeeperContainers.stream()
                                  .map(container -> container.getMappedPort(ZookeeperContainer.ZOOKEEPER_CLIENT_PORT))
                                  .map(port -> "%s:%d".formatted(LOCALHOST, port))
                                  .collect(Collectors.toSet());
    }

    private static List<ZookeeperContainer> startZookeeperCluster() {
        return IntStream.rangeClosed(1, ZOOKEEPER_CLUSTER_SIZE)
                        .parallel()
                        .mapToObj(id -> createZookeeperContainer(id, ZOOKEEPER_CLUSTER_SIZE))
                        .peek(GenericContainer::start)
                        .toList();
    }

    private static List<BookkeeperContainer> startBookkeeperCluster(Collection<ZookeeperContainer> zookeeperContainers) {
        return IntStream.rangeClosed(1, BOOKKEEPER_CLUSTER_SIZE)
                        .parallel()
                        .mapToObj(id -> createBookkeeperContainer(zookeeperContainers))
                        .peek(GenericContainer::start)
                        .toList();
    }

    private static ZookeeperContainer createZookeeperContainer(int id, int clusterSize) {
        //noinspection resource
        return new ZookeeperContainer(id, clusterSize)
                .withNetwork(NETWORK);
    }

    private static BookkeeperContainer createBookkeeperContainer(final Collection<ZookeeperContainer> zookeeperContainers) {
        //noinspection resource
        return new BookkeeperContainer(zookeeperContainers)
                .withNetwork(NETWORK);
    }
}

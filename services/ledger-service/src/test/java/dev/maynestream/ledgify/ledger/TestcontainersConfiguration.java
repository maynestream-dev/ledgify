package dev.maynestream.ledgify.ledger;

import dev.maynestream.ledgify.ledger.bookkeeper.BookkeeperContainer;
import dev.maynestream.ledgify.ledger.bookkeeper.ContainerCluster;
import dev.maynestream.ledgify.ledger.bookkeeper.ZookeeperContainer;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.Network;

import java.util.Set;
import java.util.stream.Collectors;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    private static final Network NETWORK = Network.newNetwork();
    private static final int ZOOKEEPER_CLUSTER_SIZE = 3;
    private static final int BOOKKEEPER_CLUSTER_SIZE = 3;
    private static final String LOCALHOST = "localhost";

    @Bean
    @RestartScope
    public ContainerCluster<ZookeeperContainer> zookeeperCluster() {
        //noinspection resource
        return ContainerCluster.using(ZOOKEEPER_CLUSTER_SIZE,
                                      id -> new ZookeeperContainer(id, ZOOKEEPER_CLUSTER_SIZE).withNetwork(NETWORK));
    }

    @Bean
    @RestartScope
    public ContainerCluster<BookkeeperContainer> bookkeeperCluster(ContainerCluster<ZookeeperContainer> zookeeperCluster) {
        //noinspection resource
        return ContainerCluster.using(BOOKKEEPER_CLUSTER_SIZE,
                                      id -> new BookkeeperContainer(zookeeperCluster.getContainers()).withNetwork(NETWORK));
    }

    @Bean
    @RestartScope
    public BookkeeperConfiguration bookkeeperConfiguration(ContainerCluster<ZookeeperContainer> zookeeperCluster, ContainerCluster<BookkeeperContainer> bookkeeperCluster) {
        final Set<String> zookeeperHosts = getZookeeperHosts(zookeeperCluster.getContainers());

        final BookkeeperConfiguration bookkeeperConfiguration = new BookkeeperConfiguration();
        bookkeeperConfiguration.setZkServers(String.join(",", zookeeperHosts));
        bookkeeperConfiguration.setMetadataServiceUri("zk+hierarchical://" + String.join(";", zookeeperHosts) + "/ledgers");
        if (bookkeeperCluster.getContainers().size() == 1) {
            bookkeeperConfiguration.setDefaultEnsembleSize(1);
            bookkeeperConfiguration.setDefaultWriteQuorumSize(1);
            bookkeeperConfiguration.setDefaultAckQuorumSize(1);
        }
        return bookkeeperConfiguration;
    }

    private static Set<String> getZookeeperHosts(final Set<ZookeeperContainer> zookeeperContainers) {
        return zookeeperContainers.stream()
                                  .map(container -> container.getMappedPort(ZookeeperContainer.ZOOKEEPER_CLIENT_PORT))
                                  .map(port -> "%s:%d".formatted(LOCALHOST, port))
                                  .collect(Collectors.toSet());
    }

}

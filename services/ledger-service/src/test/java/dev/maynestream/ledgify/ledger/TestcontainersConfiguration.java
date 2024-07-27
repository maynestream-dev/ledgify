package dev.maynestream.ledgify.ledger;

import dev.maynestream.ledgify.ledger.testcontainers.BookkeeperContainer;
import dev.maynestream.ledgify.ledger.testcontainers.ContainerCluster;
import dev.maynestream.ledgify.ledger.testcontainers.ZookeeperContainer;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.Network;

import java.util.Set;

import static dev.maynestream.ledgify.ledger.testcontainers.BookkeeperContainer.BOOKKEEPER_ZK_PATH;
import static dev.maynestream.ledgify.ledger.testcontainers.ZookeeperContainer.ZOOKEEPER_CLIENT_PORT;

@TestPropertySource("classpath:application.properties")
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final Network NETWORK = Network.newNetwork();
    private static final int ZOOKEEPER_CLUSTER_SIZE = 3;
    private static final int BOOKKEEPER_CLUSTER_SIZE = 3;

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
        return ContainerCluster.using(BOOKKEEPER_CLUSTER_SIZE,
                                      id -> new BookkeeperContainer(zookeeperCluster).withNetwork(NETWORK));
    }

    @Bean
    @RestartScope
    public BookkeeperConfiguration bookkeeperConfiguration(ContainerCluster<ZookeeperContainer> zookeeperCluster, ContainerCluster<BookkeeperContainer> bookkeeperCluster) {
        final Set<String> zookeeperHosts = zookeeperCluster.getHostsAsExternal(ZOOKEEPER_CLIENT_PORT);

        final BookkeeperConfiguration bookkeeperConfiguration = new BookkeeperConfiguration();
        bookkeeperConfiguration.setZkServers(String.join(",", zookeeperHosts));
        bookkeeperConfiguration.setMetadataServiceUri(ZookeeperContainer.formatMetadataServiceUri(zookeeperHosts,
                                                                                                  BOOKKEEPER_ZK_PATH));
        if (bookkeeperCluster.getContainers().size() == 1) {
            bookkeeperConfiguration.setDefaultEnsembleSize(1);
            bookkeeperConfiguration.setDefaultWriteQuorumSize(1);
            bookkeeperConfiguration.setDefaultAckQuorumSize(1);
        }
        return bookkeeperConfiguration;
    }
}

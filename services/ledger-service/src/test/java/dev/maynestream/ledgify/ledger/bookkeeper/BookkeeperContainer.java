package dev.maynestream.ledgify.ledger.bookkeeper;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.maynestream.ledgify.ledger.bookkeeper.ZookeeperContainer.ZOOKEEPER_CLIENT_PORT;

@Slf4j
public class BookkeeperContainer extends GenericContainer<BookkeeperContainer> {

    public static final String BOOKKEEPER_IMAGE_NAME = "apache/bookkeeper";

    public static final Integer BOOKIE_PORT = 3181;
    public static final Integer BOOKIE_HTTP_PORT = 8080;
    public static final Integer BOOKIE_GRPC_PORT = 4181;

    public BookkeeperContainer(final Collection<? extends GenericContainer<?>> zookeeperContainers) {
        super(DockerImageName.parse(BOOKKEEPER_IMAGE_NAME));

        final Set<String> zookeeperHosts = getZookeeperHosts(zookeeperContainers);

        this.withEnv("BK_zkServers", String.join(",", zookeeperHosts))
            .withEnv("BK_metadataServiceUri", "zk+hierarchical://%s/ledgers".formatted(String.join(";", zookeeperHosts)))
            .withEnv("BK_DATA_DIR", "/data/bookkeeper")
            .withEnv("BK_advertisedAddress", "127.0.0.1")
            .withEnv("BK_httpServerEnabled", "true");

        setWaitStrategy(new LogMessageWaitStrategy().withRegEx(".*Started component bookie-server.*"));

        this.withExposedPorts(BOOKIE_PORT, BOOKIE_HTTP_PORT, BOOKIE_GRPC_PORT)
            .withLogConsumer(c -> log.info(c.getUtf8StringWithoutLineEnding()));
    }

    private static Set<String> getZookeeperHosts(final Collection<? extends GenericContainer<?>> zookeeperContainers) {
        return zookeeperContainers.stream()
                                  .map(container -> container.getNetworkAliases()
                                                             .stream()
                                                             .findFirst()
                                                             .get())
                                  .map(host -> "%s:%d".formatted(host, ZOOKEEPER_CLIENT_PORT))
                                  .collect(Collectors.toSet());
    }
}

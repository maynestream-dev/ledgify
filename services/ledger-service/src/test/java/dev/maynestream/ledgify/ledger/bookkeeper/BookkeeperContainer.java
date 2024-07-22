package dev.maynestream.ledgify.ledger.bookkeeper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.test.util.TestSocketUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.maynestream.ledgify.ledger.bookkeeper.ZookeeperContainer.ZOOKEEPER_CLIENT_PORT;

@Slf4j
public class BookkeeperContainer extends GenericContainer<BookkeeperContainer> {

    public static final String BOOKKEEPER_IMAGE_NAME = "apache/bookkeeper";

//    public static final Integer DEFAULT_BOOKIE_PORT = 3181;
    public static final Integer BOOKIE_HTTP_PORT = 8080;
    public static final Integer BOOKIE_GRPC_PORT = 4181;

    private final int bookiePort;

    public BookkeeperContainer(final Collection<? extends GenericContainer<?>> zookeeperContainers) {
        super(DockerImageName.parse(BOOKKEEPER_IMAGE_NAME));

        checkZookeeper(zookeeperContainers);

        bookiePort = TestSocketUtils.findAvailableTcpPort();

        log.info("Starting Bookkeeper using bookie port: {}", bookiePort);

        final Set<String> zookeeperHosts = getZookeeperHosts(zookeeperContainers);

        this.withEnv("BK_zkServers", String.join(",", zookeeperHosts))
            .withEnv("BK_metadataServiceUri", "zk+hierarchical://%s/ledgers".formatted(String.join(";", zookeeperHosts)))
            .withEnv("BK_httpServerEnabled", "true")
            .withEnv("BK_useHostNameAsBookieID", "true")
            .withEnv("BK_useShortHostName", "true")
            .withEnv("BK_advertisedAddress", "localhost")
            .withEnv("BK_bookiePort", String.valueOf(bookiePort));

        setWaitStrategy(new LogMessageWaitStrategy().withRegEx(".*Started component bookie-server.*")
                                                    .withStartupTimeout(Duration.ofSeconds(120)));

        // while this port is available on the host - it might not actually always be available on the container =/
        addFixedExposedPort(bookiePort, bookiePort);

        this.withExposedPorts(BOOKIE_HTTP_PORT, BOOKIE_GRPC_PORT)
            .withLogConsumer(c -> log.info(c.getUtf8StringWithoutLineEnding()));
    }

    private static void checkZookeeper(final Collection<? extends GenericContainer<?>> zookeeperContainers) {
        if (zookeeperContainers == null || zookeeperContainers.isEmpty()) {
            throw new IllegalArgumentException("must provide at least 1 zookeeper container reference");
        }
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

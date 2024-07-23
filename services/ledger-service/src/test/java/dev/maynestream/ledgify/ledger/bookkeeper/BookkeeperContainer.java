package dev.maynestream.ledgify.ledger.bookkeeper;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.test.util.TestSocketUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Set;

import static dev.maynestream.ledgify.ledger.bookkeeper.ZookeeperContainer.ZOOKEEPER_CLIENT_PORT;

@Slf4j
public class BookkeeperContainer extends GenericContainer<BookkeeperContainer> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("apache/bookkeeper");

    public static final String BOOKKEEPER_ZK_PATH = "ledgers";
    public static final Integer BOOKIE_HTTP_PORT = 8080;
    public static final Integer BOOKIE_GRPC_PORT = 4181;

    private final int bookiePort;
    private final String metadataServiceUri;

    private Level level = Level.DEBUG;

    public BookkeeperContainer(final ContainerCluster<ZookeeperContainer> zookeeperCluster) {
        super(DEFAULT_IMAGE_NAME);

        bookiePort = TestSocketUtils.findAvailableTcpPort();
        log.info("Starting Bookkeeper using bookie port: {}", bookiePort);

        final Set<String> zookeeperHosts = zookeeperCluster.getHostsAsInternal(ZOOKEEPER_CLIENT_PORT);
        metadataServiceUri = ZookeeperContainer.formatMetadataServiceUri(zookeeperHosts, BOOKKEEPER_ZK_PATH);

        this.withEnv("BK_zkServers", String.join(",", zookeeperHosts))
            .withEnv("BK_metadataServiceUri", metadataServiceUri)
            .withEnv("BK_httpServerEnabled", "true")
            .withEnv("BK_useHostNameAsBookieID", "true")
            .withEnv("BK_useShortHostName", "true")
            .withEnv("BK_advertisedAddress", "localhost")
            .withEnv("BK_bookiePort", String.valueOf(bookiePort));

        // this is required to allow the docker host to connect on the same port that is published to zookeeper
        // NOTE: while this port is available on the host - it might not actually always be available on the container =/
        this.addFixedExposedPort(bookiePort, bookiePort);
        this.withExposedPorts(BOOKIE_HTTP_PORT, BOOKIE_GRPC_PORT);

        this.withLogConsumer(c -> log.atLevel(level).log(c.getUtf8StringWithoutLineEnding()));

        this.setWaitStrategy(new LogMessageWaitStrategy().withRegEx(".*Started component bookie-server.*")
                                                         // clusters will take longer to start, even in parallel
                                                         .withStartupTimeout(Duration.ofSeconds(120)));
    }

    public BookkeeperContainer withLogLevel(Level level) {
        this.level = level;
        return this;
    }

    public int getBookiePort() {
        return bookiePort;
    }

    public String getMetadataServiceUri() {
        return metadataServiceUri;
    }
}

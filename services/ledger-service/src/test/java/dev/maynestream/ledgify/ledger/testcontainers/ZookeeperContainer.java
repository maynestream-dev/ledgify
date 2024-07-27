package dev.maynestream.ledgify.ledger.testcontainers;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class ZookeeperContainer extends GenericContainer<ZookeeperContainer> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("zookeeper");

    public static final String ZOOKEEPER_HOST = "zookeeper";
    public static final Integer ZOOKEEPER_CLIENT_PORT = 2181;
    public static final Integer ZOOKEEPER_FOLLOWER_PORT = 2888;
    public static final Integer ZOOKEEPER_SERVER_PORT = 3888;
    public static final Integer ZOOKEEPER_HTTP_PORT = 8080;

    private Level level = Level.DEBUG;

    public ZookeeperContainer(final int id, final int clusterSize) {
        super(DEFAULT_IMAGE_NAME);

        this.withEnv("ZOO_MY_ID", String.valueOf(id))
            .withEnv("ZOO_SERVERS", asServers(clusterSize))
            .withEnv("ZOO_STANDALONE_ENABLED", "false");

        this.withNetworkAliases(asHostname(id));

        this.withExposedPorts(ZOOKEEPER_CLIENT_PORT,
                              ZOOKEEPER_FOLLOWER_PORT,
                              ZOOKEEPER_SERVER_PORT,
                              ZOOKEEPER_HTTP_PORT);

        this.withLogConsumer(c -> log.atLevel(level).log(c.getUtf8StringWithoutLineEnding()));

        this.setWaitStrategy(new LogMessageWaitStrategy().withRegEx(".*Started AdminServer.*"));
    }

    public ZookeeperContainer withLogLevel(Level level) {
        this.level = level;
        return this;
    }

    private static String asServers(int clusterSize) {
        return IntStream.rangeClosed(1, clusterSize)
                        .mapToObj(id -> "server.%d=%s:%d:%d;%d".formatted(id, asHostname(id), ZOOKEEPER_FOLLOWER_PORT, ZOOKEEPER_SERVER_PORT, ZOOKEEPER_CLIENT_PORT))
                        .collect(Collectors.joining(" "));
    }

    private static String asHostname(final int id) {
        return "%s-%d".formatted(ZOOKEEPER_HOST, id);
    }

    public static String formatMetadataServiceUri(Set<String> hosts, String path) {
        return "zk+hierarchical://%s/%s".formatted(String.join(";", hosts), path);
    }
}

package dev.maynestream.ledgify.ledger.bookkeeper;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class ZookeeperContainer extends GenericContainer<ZookeeperContainer> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("zookeeper");

    public static final String ZOOKEEPER_HOST = "zookeeper";
    public static final Integer ZOOKEEPER_CLIENT_PORT = 2181;
    public static final Integer ZOOKEEPER_FOLLOWER_PORT = 2888;
    public static final Integer ZOOKEEPER_SERVER_PORT = 3888;
    public static final Integer ZOOKEEPER_HTTP_PORT = 8080;

    public ZookeeperContainer(final int id, final int clusterSize) {
        super(DEFAULT_IMAGE_NAME);

        this.withEnv("ZOO_MY_ID", String.valueOf(id))
            .withEnv("ZOO_SERVERS",
                     "server.1=zookeeper-1:2888:3888;2181 server.2=zookeeper-2:2888:3888;2181 server.3=zookeeper-3:2888:3888;2181")
            .withEnv("ZOO_STANDALONE_ENABLED", "false")
            .withEnv("ZOO_INIT_LIMIT", "10")
            .withEnv("ZOO_SYNC_LIMIT", "5")
            .withStartupAttempts(2);

        setWaitStrategy(new LogMessageWaitStrategy().withRegEx(".*Started AdminServer.*"));

        this.withExposedPorts(ZOOKEEPER_CLIENT_PORT,
                              ZOOKEEPER_FOLLOWER_PORT,
                              ZOOKEEPER_SERVER_PORT,
                              ZOOKEEPER_HTTP_PORT)
            .withLogConsumer(c -> log.info(c.getUtf8StringWithoutLineEnding()));
    }
}

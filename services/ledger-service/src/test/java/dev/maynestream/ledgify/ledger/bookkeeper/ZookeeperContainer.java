package dev.maynestream.ledgify.ledger.bookkeeper;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class ZookeeperContainer extends GenericContainer<ZookeeperContainer> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("zookeeper");

    public static final String ZOOKEEPER_HOST = "zookeeper";
    public static final Integer ZOOKEEPER_PORT = 2181;

    public ZookeeperContainer() {
        this(DEFAULT_IMAGE_NAME);
    }

    public ZookeeperContainer(final String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public ZookeeperContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);

        this.withExposedPorts(ZOOKEEPER_PORT)
            .withNetworkAliases(ZOOKEEPER_HOST)
            .withLogConsumer(c -> log.info(c.getUtf8StringWithoutLineEnding()));
    }
}

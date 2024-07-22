package dev.maynestream.ledgify.ledger.bookkeeper;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class BookkeeperContainer extends GenericContainer<BookkeeperContainer> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("apache/bookkeeper");

    public static final Integer BOOKIE_PORT = 3181;
    public static final Integer BOOKIE_HTTP_PORT = 8080;

    public BookkeeperContainer() {
        this(DEFAULT_IMAGE_NAME);
    }

    public BookkeeperContainer(final String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public BookkeeperContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);

        this.withExposedPorts(BOOKIE_PORT, BOOKIE_HTTP_PORT)
            .withLogConsumer(c -> log.info(c.getUtf8StringWithoutLineEnding()));
    }
}

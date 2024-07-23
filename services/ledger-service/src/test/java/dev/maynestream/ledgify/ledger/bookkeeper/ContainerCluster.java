package dev.maynestream.ledgify.ledger.bookkeeper;

import lombok.Getter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.lifecycle.Startables;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterators.spliterator;

@Getter
public class ContainerCluster<T extends GenericContainer<T> & Startable> implements Startable {

    public static <T extends GenericContainer<T> & Startable> ContainerCluster<T> using(int clusterSize, Function<Integer, T> containerSupplier) {
        if (clusterSize < 1) {
            throw new IllegalArgumentException("Cluster size must be greater than 0");
        }

        return new ContainerCluster<>(IntStream.rangeClosed(1, clusterSize)
                                               .mapToObj(containerSupplier::apply)
                                               .collect(Collectors.toSet()));
    }

    private static final String LOCALHOST = "localhost";

    private final Set<T> containers;

    private ContainerCluster(final Set<T> containers) {
        this.containers = Set.copyOf(containers);
    }

    @Override
    public Set<Startable> getDependencies() {
        return Set.copyOf(containers);
    }

    @Override
    public void start() {
        Startables.deepStart(containers).join();
    }

    @Override
    public void stop() {
        containers.forEach(Startable::stop);
    }

    public Stream<T> stream() {
        return StreamSupport.stream(spliterator(containers, 0), false);
    }

    public Set<String> getHostsAsExternal(int exposedPort) {
        return stream().map(container -> container.getMappedPort(exposedPort))
                       .map(port -> "%s:%d".formatted(LOCALHOST, port))
                       .collect(Collectors.toSet());
    }

    public Set<String> getHostsAsInternal(int exposedPort) {
        return stream().map(container -> container.getNetworkAliases().stream().findFirst().get())
                       .map(host -> "%s:%d".formatted(host, exposedPort))
                       .collect(Collectors.toSet());
    }
}

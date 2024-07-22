package dev.maynestream.ledgify.ledger.bookkeeper;

import lombok.Getter;
import org.testcontainers.containers.Container;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.lifecycle.Startables;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Getter
public class ContainerCluster<T extends Container<T> & Startable> implements Startable {

    public static <T extends Container<T> & Startable> ContainerCluster<T> using(int clusterSize, Function<Integer, T> containerSupplier) {
        return new ContainerCluster<>(IntStream.rangeClosed(1, clusterSize)
                                               .mapToObj(containerSupplier::apply)
                                               .collect(Collectors.toSet()));
    }

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
}

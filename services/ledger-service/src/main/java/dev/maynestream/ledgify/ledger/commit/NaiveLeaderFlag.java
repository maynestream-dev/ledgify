package dev.maynestream.ledgify.ledger.commit;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;

import java.util.Objects;
import java.util.UUID;

@Slf4j
public class NaiveLeaderFlag extends LeaderSelectorListenerAdapter implements AutoCloseable {
    private static final String LEDGERS_ELECT_PATH_FORMAT = "/ledgers-elect/%s";

    private final LeaderSelector leaderSelector;
    private final UUID uniqueId;

    public NaiveLeaderFlag(final CuratorFramework curator, final UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId cannot be null");

        this.leaderSelector = initialiseLeaderSelector(curator, uniqueId);
        this.uniqueId = uniqueId;
    }

    public boolean isLeader() {
        return leaderSelector.hasLeadership();
    }

    @Override
    public void takeLeadership(final CuratorFramework client) {
        synchronized (this) {
            log.info("Becoming leader - {}", uniqueId);

            try {
                while (true) {
                    this.wait();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        leaderSelector.close();
    }

    private LeaderSelector initialiseLeaderSelector(final CuratorFramework curator, final UUID uniqueId) {
        final LeaderSelector leaderSelector = new LeaderSelector(curator,
                                                                 LEDGERS_ELECT_PATH_FORMAT.formatted(uniqueId),
                                                                 this);
        leaderSelector.autoRequeue();
        leaderSelector.start();

        return leaderSelector;
    }
}

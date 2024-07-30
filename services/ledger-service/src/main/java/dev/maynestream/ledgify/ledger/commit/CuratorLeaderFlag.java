package dev.maynestream.ledgify.ledger.commit;

import dev.maynestream.ledgify.ledger.commit.logging.LedgerLoggingContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;

import java.util.Objects;
import java.util.UUID;

@Slf4j
public class CuratorLeaderFlag extends LeaderSelectorListenerAdapter implements AutoCloseable {
    private static final String LEDGERS_ELECT_PATH_FORMAT = "/ledgers-elect/%s";

    private final LeaderSelector leaderSelector;
    private final UUID uniqueId;
    private volatile Thread curatorThread;

    public CuratorLeaderFlag(final CuratorFramework curator, final UUID electionGroupId, final UUID uniqueId) {
        Objects.requireNonNull(curator, "curator cannot be null");
        Objects.requireNonNull(electionGroupId, "uniqueId cannot be null");
        Objects.requireNonNull(uniqueId, "uniqueId cannot be null");

        this.leaderSelector = initialiseLeaderSelector(curator, electionGroupId);
        this.uniqueId = uniqueId;
    }

    public boolean isLeader() {
        return leaderSelector.hasLeadership();
    }

    @Override
    public void takeLeadership(final CuratorFramework client) {
        synchronized (this) {
            try (final var ignore = LedgerLoggingContext.ledger(true, uniqueId)) {
                log.info("Becoming leader");
            }

            try {
                while (true) {
                    curatorThread = Thread.currentThread();

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

    public void interrupted() {
        final Thread curatorThread = this.curatorThread;
        if (curatorThread != null) {
            curatorThread.interrupt();
        }
    }
}

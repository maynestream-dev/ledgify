package dev.maynestream.ledgify.ledger.transaction;

import dev.maynestream.ledgify.ledger.BookkeeperConfiguration;
import dev.maynestream.ledgify.ledger.commit.Ledger.Entry;
import dev.maynestream.ledgify.transaction.Transaction;
import org.apache.bookkeeper.client.BookKeeper;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service
public class TransactionCommitterFactory implements AutoCloseable {

    private final List<TransactionCommitter> committers = new CopyOnWriteArrayList<>();

    private final BookKeeper bookkeeper;
    private final BookkeeperConfiguration bookkeeperConfiguration;
    private final CuratorFramework curator;

    public TransactionCommitterFactory(final BookKeeper bookkeeper,
                                       final BookkeeperConfiguration bookkeeperConfiguration,
                                       final CuratorFramework curator) {
        this.bookkeeper = bookkeeper;
        this.bookkeeperConfiguration = bookkeeperConfiguration;
        this.curator = curator;
    }

    public TransactionCommitter create(final TransactionLog log, final UUID accountId, final LocalDate date) {
        return track(new TransactionCommitter(UUID.randomUUID(),
                                              bookkeeper,
                                              bookkeeperConfiguration,
                                              curator,
                                              log,
                                              accountId,
                                              date));
    }

    public TransactionReader createReader(final UUID accountId,
                                          final LocalDate date,
                                          final Consumer<Entry<Transaction>> consumer) {
        return new TransactionReader(UUID.randomUUID(),
                                     bookkeeper,
                                     bookkeeperConfiguration,
                                     curator,
                                     accountId,
                                     date,
                                     consumer);
    }

    @Override
    public void close() {
        for (TransactionCommitter committer : committers) {
            try {
                committer.close();
            } catch (Exception ignored) {
            }
        }
    }

    private TransactionCommitter track(final TransactionCommitter committer) {
        committers.add(committer);
        return committer;
    }
}

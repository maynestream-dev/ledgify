package dev.maynestream.ledgify.ledger.transaction;

import dev.maynestream.ledgify.ledger.BookkeeperConfiguration;
import dev.maynestream.ledgify.ledger.commit.CuratorLeaderFlag;
import dev.maynestream.ledgify.ledger.commit.Ledger;
import dev.maynestream.ledgify.ledger.commit.Ledger.Entry;
import dev.maynestream.ledgify.ledger.commit.Ledger.LedgerException;
import dev.maynestream.ledgify.ledger.commit.LedgerAccessor;
import dev.maynestream.ledgify.ledger.commit.LedgerCollectionStore;
import dev.maynestream.ledgify.ledger.commit.LedgerCommitter;
import dev.maynestream.ledgify.ledger.transaction.logging.TransactionLoggingContext;
import dev.maynestream.ledgify.transaction.Transaction;
import lombok.SneakyThrows;
import org.apache.bookkeeper.client.BookKeeper;
import org.apache.curator.framework.CuratorFramework;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class TransactionCommitter extends LedgerCommitter<Transaction> {

    private static final String DAILY_LEDGER_PATH_FORMAT = "%s/%s";

    private final TransactionLog transactions;
    private final UUID accountId;

    public TransactionCommitter(final UUID uniqueId,
                                final BookKeeper bookKeeper,
                                final BookkeeperConfiguration bookkeeperConfiguration,
                                final CuratorFramework curator,
                                final TransactionLog log,
                                final UUID accountId,
                                final LocalDate date) {
        super(uniqueId,
              new LedgerAccessor(bookKeeper, bookkeeperConfiguration, accountId.toString().getBytes()),
              new LedgerCollectionStore(curator, DAILY_LEDGER_PATH_FORMAT.formatted(accountId, date)),
              new CuratorLeaderFlag(curator, accountId, uniqueId),
              t -> { },
              TransactionCommitter::parse);
        this.transactions = log;
        this.accountId = accountId;
    }

    @Override
    public void run() {
        try (final var ignore = TransactionLoggingContext.account(accountId)) {
            super.run();
        }
    }

    @Override
    protected Entry<Transaction> attemptCommit(final Ledger ledger, Entry<Transaction> lastRecordedEntry) {
        log.info("Attempting to commit transaction to ledger {}", ledger.getId());
        final AtomicReference<Entry<Transaction>> committedEntry = new AtomicReference<>(lastRecordedEntry);
        doCommit(ledger, committedEntry);
        return committedEntry.get();
    }

    private void doCommit(final Ledger ledger, final AtomicReference<Entry<Transaction>> committedEntry) {
        try {
            transactions.awaitCommit(transaction -> {
                final Entry<Transaction> entry = ledger.addEntry(transaction, Transaction::toByteArray);
                committedEntry.set(entry);
                log.info("Committed transaction as entry {}", entry.entryId());
                return entry.entryId();
            });
        } catch (Exception e) {
            log.warn("Failed to commit transaction", e);
            throw new LedgerException(committedEntry.get(), e);
        }
    }

    @SneakyThrows
    private static Transaction parse(byte[] bytes) {
        return Transaction.parseFrom(bytes);
    }
}

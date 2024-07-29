package dev.maynestream.ledgify.ledger.transaction;

import dev.maynestream.ledgify.ledger.BookkeeperConfiguration;
import dev.maynestream.ledgify.ledger.commit.Ledger;
import dev.maynestream.ledgify.ledger.commit.Ledger.Entry;
import dev.maynestream.ledgify.ledger.commit.Ledger.LedgerException;
import dev.maynestream.ledgify.ledger.commit.LedgerAccessor;
import dev.maynestream.ledgify.ledger.commit.LedgerCollectionStore;
import dev.maynestream.ledgify.ledger.commit.LedgerCommitter;
import dev.maynestream.ledgify.ledger.commit.NaiveLeaderFlag;
import dev.maynestream.ledgify.transaction.Transaction;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.bookkeeper.client.BookKeeper;
import org.apache.curator.framework.CuratorFramework;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
public class TransactionCommitter extends LedgerCommitter<Transaction> implements Runnable {

    private static final String DAILY_LEDGER_PATH_FORMAT = "%s/%s";

    private final TransactionLog transactions;

    public TransactionCommitter(final BookKeeper bookKeeper,
                                final BookkeeperConfiguration bookkeeperConfiguration,
                                final CuratorFramework curator,
                                final TransactionLog log,
                                final UUID uniqueId,
                                final LocalDate date) {
        super(new LedgerAccessor(bookKeeper, bookkeeperConfiguration, uniqueId.toString().getBytes()),
              new LedgerCollectionStore(curator, DAILY_LEDGER_PATH_FORMAT.formatted(uniqueId, date)),
              new NaiveLeaderFlag(curator, uniqueId),
              transactionLogger(uniqueId, date),
              TransactionCommitter::parse);
        this.transactions = log;
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
                committedEntry.set(ledger.addEntry(transaction, Transaction::toByteArray));
            });
        } catch (Exception e) {
            log.warn("Failed to commit transaction", e);
            throw new LedgerException(committedEntry.get(), e);
        }
    }

    private static Consumer<Entry<Transaction>> transactionLogger(final UUID uniqueId, final LocalDate date) {
        return transaction -> {
            log.info("Transaction {} committed to account ledger {} for {}",
                     transaction.data().getTransactionId(),
                     uniqueId,
                     date);
        };
    }

    @SneakyThrows
    private static Transaction parse(byte[] bytes) {
        return Transaction.parseFrom(bytes);
    }
}

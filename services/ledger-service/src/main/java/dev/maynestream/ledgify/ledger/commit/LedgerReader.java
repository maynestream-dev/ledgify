package dev.maynestream.ledgify.ledger.commit;

import dev.maynestream.ledgify.ledger.commit.Ledger.Entry;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.KeeperException;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
public abstract class LedgerReader<T> {
    private static final int LEDGER_COMMIT_AWAIT_MILLIS = 1000;

    final LedgerAccessor accessor;
    final LedgerCollectionStore store;
    final Consumer<Entry<T>> consumer;
    final Function<byte[], T> transformer;

    protected LedgerReader(final LedgerAccessor accessor,
                           final LedgerCollectionStore store,
                           final Consumer<Entry<T>> consumer,
                           final Function<byte[], T> transformer) {
        this.accessor = Objects.requireNonNull(accessor, "accessor cannot be null");
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.consumer = Objects.requireNonNull(consumer, "consumer cannot be null");
        this.transformer = Objects.requireNonNull(transformer, "transformer cannot be null");
    }

    protected boolean isLeader() {
        return false;
    }

    public void readAll() throws Exception {
        Entry<T> lastRecordedEntry = Entry.initial();

        log.info("Reading all entries from {}", lastRecordedEntry);
        while (!Thread.interrupted() && !isLeader()) {
            lastRecordedEntry = readFrom(lastRecordedEntry);

            Thread.sleep(LEDGER_COMMIT_AWAIT_MILLIS);
        }
    }

    protected Entry<T> readFrom(final Entry<T> entry) throws Exception {
        LedgerCollection ledgers = awaitInitialLedgerCollection(entry);

        Entry<T> lastRecordedEntry = entry;
        while (!isLeader()) {
            // record all unread transactions
            lastRecordedEntry = consumeUnrecordedTransactionsAsFollower(lastRecordedEntry, ledgers);

            // update the list of unread transaction entries
            ledgers = store.load().since(lastRecordedEntry.ledgerId());
        }

        return lastRecordedEntry;
    }

    private LedgerCollection awaitInitialLedgerCollection(final Entry<T> entry) throws Exception {
        LedgerCollection ledgers = null;

        log.info("Waiting for initial ledger collection");
        // wait for leader to write
        while (!isLeader() && ledgers == null) {
            try {
                ledgers = store.load();
                log.debug("Loaded initial ledger collection {}", ledgers);
                if (entry.exists()) { // only get ledgers that haven't been seen
                    ledgers = ledgers.since(entry.ledgerId());
                    log.debug("Truncating initial ledger collection to {}", ledgers);
                }
                // on first load leader may not yet have created ledger collection
            } catch (KeeperException.NoNodeException nne) {
                log.debug("No ledger collection found - awaiting initialization by leader");
                Thread.sleep(1000);
            }
        }
        return ledgers;
    }

    private Entry<T> consumeUnrecordedTransactionsAsFollower(Entry<T> lastRecordedEntry,
                                                             final LedgerCollection ledgers) throws Exception {
        for (long ledgerId : ledgers) {
            long startingEntry = 0;
            while (!isLeader()) {
                // if the last recorded entry was part of this ledger, only read entries since then
                if (lastRecordedEntry.ledgerId() == ledgerId) {
                    startingEntry = lastRecordedEntry.entryId() + 1;
                }

                log.debug("Reading ledger {} to consume from entry {}", ledgerId, startingEntry);
                final Ledger ledger = accessor.openForRead(ledgerId);

                // if there are unread entries remaining on this ledger (according to the read-only handle)
                if (startingEntry <= ledger.getLastRecordedEntryId()) {
                    // read and record the remaining entries in the ledger
                    lastRecordedEntry = consumeEntries(ledger, lastRecordedEntry, startingEntry);
                }

                // if this ledger is closed, eagerly progress to the next
                if (ledger.isClosed()) {
                    log.debug("Finished reading from closed ledger {}", ledgerId);
                    break;
                }

                log.debug("Awaiting new commits to open ledger {}", ledgerId);
                // otherwise await some more entries
                Thread.sleep(LEDGER_COMMIT_AWAIT_MILLIS);
            }
        }

        return lastRecordedEntry;
    }

    private Entry<T> consumeEntries(final Ledger ledger,
                                    final Entry<T> lastRecordedEntry,
                                    final long nextEntry) throws Exception {
        return ledger.consumeEntries(nextEntry, lastRecordedEntry, consumer, transformer);
    }
}

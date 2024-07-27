package dev.maynestream.ledgify.ledger.commit;

import dev.maynestream.ledgify.ledger.commit.Ledger.Entry;
import org.apache.zookeeper.KeeperException;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

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
        Entry<T> lasRecordedEntry = Entry.initial();
        while (!Thread.interrupted()) {
            lasRecordedEntry = readFrom(lasRecordedEntry);

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

    private LedgerCollection awaitInitialLedgerCollection(final Entry entry) throws Exception {
        LedgerCollection ledgers = null;

        // wait for leader to write
        while (ledgers == null) {
            try {
                ledgers = store.load();
                if (entry.exists()) { // only get ledgers that haven't been seen
                    ledgers = ledgers.since(entry.ledgerId());
                }
                // on first load leader may not yet have created ledger collection
            } catch (KeeperException.NoNodeException nne) {
                Thread.sleep(1000);
            }
        }
        return ledgers;
    }

    private Entry<T> consumeUnrecordedTransactionsAsFollower(Entry<T> lastRecordedEntry,
                                                             final LedgerCollection ledgers) throws Exception {
        for (long previous : ledgers) {
            long nextEntry = 0;
            while (!isLeader()) {
                // if the last recorded entry was part of this ledger, only read entries since then
                if (lastRecordedEntry.ledgerId() == previous) {
                    nextEntry = lastRecordedEntry.entryId() + 1;
                }

                final Ledger ledger = accessor.openForRead(previous);

                // if there are unread entries remaining on this ledger (according to the read-only handle)
                if (nextEntry <= ledger.getLastRecordedEntryId()) {
                    // read and record the remaining entries in the ledger
                    lastRecordedEntry = consumeEntries(ledger, lastRecordedEntry, nextEntry);
                }

                // if this ledger is closed, eagerly progress to the next
                if (ledger.isClosed()) {
                    break;
                }

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

package dev.maynestream.ledgify.ledger.commit;

import dev.maynestream.ledgify.ledger.commit.Ledger.Entry;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class LedgerReader<T> {
    private static final int LEDGER_COMMIT_AWAIT_MILLIS = 1000;

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final UUID uniqueId;
    final LedgerAccessor accessor;
    final LedgerCollectionStore store;
    final Consumer<Entry<T>> consumer;
    final Function<byte[], T> transformer;

    protected LedgerReader(final UUID uniqueId,
                           final LedgerAccessor accessor,
                           final LedgerCollectionStore store,
                           final Consumer<Entry<T>> consumer,
                           final Function<byte[], T> transformer) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId cannot be null");
        this.accessor = Objects.requireNonNull(accessor, "accessor cannot be null");
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.consumer = Objects.requireNonNull(consumer, "consumer cannot be null");
        this.transformer = Objects.requireNonNull(transformer, "transformer cannot be null");
    }

    public boolean isLeader() {
        return false;
    }

    public void readAll() throws Exception {
        readAll(false);
    }

    public void readAll(final boolean follow) throws Exception {
        readFrom(Entry.initial(), follow);
    }

    protected Entry<T> readFrom(final Entry<T> entry, final boolean follow) throws Exception {
        log.info("Reading entries from {}", entry);

        Entry<T> lastRecordedEntry = entry;
        do {
            LedgerCollection ledgers = load(lastRecordedEntry, follow); // initial load

            if (ledgers == null) {
                log.debug("No ledgers found and follow not specified");
                return lastRecordedEntry;
            }

            do {
                // record all unread entries
                lastRecordedEntry = consumeUnrecorded(lastRecordedEntry, ledgers, follow);

                // load any updates
                ledgers = load(lastRecordedEntry, false);

                // continue until there's new no ledgers to consume (break early if we're now leader)
            } while (!isLeader() && follow && !ledgers.since(lastRecordedEntry.ledgerId()).isEmpty());

            // await potential new commits to the
            if (follow) {
                Thread.sleep(LEDGER_COMMIT_AWAIT_MILLIS);
            }
        } while (!Thread.interrupted() && !isLeader() && follow);

        return lastRecordedEntry;
    }

    private LedgerCollection load(final Entry<T> entry, final boolean await) throws Exception {
        LedgerCollection ledgers = null;

        log.info("Loading ledger collection since {}", entry.ledgerId());

        // wait for leader to write
        do {
            try {
                ledgers = store.load();
                log.debug("Loaded ledger collection {}", ledgers);
                if (entry.exists()) { // only get ledgers that haven't been seen
                    ledgers = ledgers.since(entry.ledgerId());
                    log.debug("Truncating ledger collection to {}", ledgers);
                }
                // on first load leader may not yet have created ledger collection
            } catch (KeeperException.NoNodeException nne) {
                log.debug("No ledger collection found - awaiting initialization by leader");
                Thread.sleep(1000);
            }
        } while (!isLeader() && ledgers == null && await);

        log.info("Loaded ledger collection {}", ledgers);

        return ledgers;
    }

    private Entry<T> consumeUnrecorded(Entry<T> lastRecordedEntry,
                                       final LedgerCollection ledgers,
                                       final boolean follow) throws Exception {
        for (long ledgerId : ledgers) {
            long startingEntry = 0;
            while (!isLeader()) {
                // if the last recorded entry was part of this ledger, only read entries since then
                if (lastRecordedEntry.ledgerId() == ledgerId) {
                    startingEntry = lastRecordedEntry.entryId() + 1;
                }

                log.debug("Opening ledger {}", ledgerId);
                final Ledger ledger = accessor.openForRead(ledgerId);

                // if there are unread entries remaining on this ledger (according to the read-only handle)
                if (startingEntry <= ledger.getLastRecordedEntryId()) {
                    log.info("Reading ledger {} to consume from entry {}", ledgerId, startingEntry);
                    // read and record the remaining entries in the ledger
                    lastRecordedEntry = consumeEntries(ledger, lastRecordedEntry, startingEntry);
                    log.debug("Read from ledger {} to entry {}", ledgerId, lastRecordedEntry.entryId());
                }

                // if this ledger is closed, eagerly progress to the next
                if (ledger.isClosed()) {
                    log.debug("Finished reading from closed ledger {}", ledgerId);
                    break;
                }

                if (!follow) {
                    log.debug("Eagerly breaking from no-follow open ledger {}", ledgerId);
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

package dev.maynestream.ledgify.ledger.commit;

import dev.maynestream.ledgify.ledger.commit.Ledger.Entry;
import dev.maynestream.ledgify.ledger.commit.Ledger.LedgerException;
import lombok.SneakyThrows;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class LedgerCommitter<T> extends LedgerReader<T> implements AutoCloseable, Runnable {

    private final NaiveLeaderFlag flag;

    protected LedgerCommitter(final LedgerAccessor accessor,
                              final LedgerCollectionStore store,
                              final NaiveLeaderFlag flag,
                              final Consumer<Entry<T>> consumer,
                              final Function<byte[], T> transformer) {
        super(accessor, store, consumer, transformer);
        this.flag = Objects.requireNonNull(flag, "flag cannot be null");
    }

    @Override
    public void close() {
        flag.close();
    }

    @Override
    protected boolean isLeader() {
        return flag.isLeader();
    }

    @SneakyThrows
    @Override
    public void run() {
        Entry<T> lastDisplayedEntry = Entry.initial();

        while (true) {
            if (isLeader()) {
                lastDisplayedEntry = lead(lastDisplayedEntry);
            } else {
                lastDisplayedEntry = readFrom(lastDisplayedEntry);
            }

            Thread.sleep(1000);
        }
    }

    protected abstract Entry<T> attemptCommit(final Ledger ledger, Entry<T> lastRecordedEntry);

    protected Entry<T> lead(final Entry<T> entry) throws Exception {
        Entry<T> lastRecordedEntry = entry;

        final Stat stat = new Stat();
        final LedgerCollection ledgers = store.load(stat);

        try {
            lastRecordedEntry = consumeUnrecordedTransactionsAsLeader(lastRecordedEntry, ledgers);

            final Ledger ledger = createNewLedger(stat, ledgers, lastRecordedEntry);

            try {
                while (flag.isLeader()) {
                    // attempt to commit transactions

                    lastRecordedEntry = attemptCommit(ledger, lastRecordedEntry);
                }

                // close this ledger once we're no longer leader
                ledger.close();
            } catch (Exception e) {
                // let it fall through to the return
            }
        } catch (LedgerException e) {
            lastRecordedEntry = e.getLastRecordedEntry();
        }

        return lastRecordedEntry;
    }

    private Entry<T> consumeUnrecordedTransactionsAsLeader(Entry<T> lastRecordedEntry,
                                                           final LedgerCollection ledgers) throws Exception {
        final LedgerCollection missedLedgers = missedLedgers(lastRecordedEntry, ledgers);

        long nextEntry = lastRecordedEntry.entryId() + 1;

        for (Long previous : missedLedgers) {
            //try to open the missed ledger
            Ledger ledger;
            try {
                ledger = accessor.openAsLeader(previous);
            } catch (Exception e) {
                throw new LedgerException(lastRecordedEntry);
            }

            // reset and continue if the end of the ledger has been reached
            if (nextEntry > ledger.getLastRecordedEntryId()) {
                nextEntry = 0;
                continue;
            }

            // otherwise read and record the remaining entries in the ledger
            lastRecordedEntry = consumeEntries(ledger, lastRecordedEntry, nextEntry);
        }

        return lastRecordedEntry;
    }

    private Ledger createNewLedger(final Stat stat,
                                   final LedgerCollection ledgers,
                                   final Entry lastRecordedEntry) throws Exception {
        final Ledger ledger = accessor.create();

        try {
            boolean newLedger = ledgers.isEmpty();
            ledgers.append(ledger.getId());

            if (newLedger) {
                store.create(stat, ledgers);
            } else {
                store.update(stat, ledgers);
            }
        } catch (KeeperException.BadVersionException | KeeperException.NodeExistsException e) {
            throw new LedgerException(lastRecordedEntry);
        }
        return ledger;
    }

    private Entry<T> consumeEntries(final Ledger ledger,
                                    final Entry<T> lastRecordedEntry,
                                    final long nextEntry) throws Exception {
        return ledger.consumeEntries(nextEntry, lastRecordedEntry, consumer, transformer);
    }

    private static <T> LedgerCollection missedLedgers(final Entry<T> lastRecordedEntry,
                                                      final LedgerCollection ledgers) {
        LedgerCollection toRead = ledgers;
        if (lastRecordedEntry.exists()) {
            toRead = ledgers.since(lastRecordedEntry.ledgerId());
        }
        return toRead;
    }
}

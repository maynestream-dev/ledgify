package dev.maynestream.ledgify.ledger.commit;

import org.apache.bookkeeper.client.BKException;
import org.apache.bookkeeper.client.LedgerEntry;
import org.apache.bookkeeper.client.LedgerHandle;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class Ledger {
    private final LedgerHandle ledgerHandle;

    Ledger(final LedgerHandle ledgerHandle) {
        this.ledgerHandle = ledgerHandle;
    }

    public long getId() {
        return ledgerHandle.getId();
    }

    public long getLastRecordedEntryId() {
        return ledgerHandle.getLastAddConfirmed();
    }

    public <T> Entry<T> addEntry(final T data, final Function<T, byte[]> transformer) throws Exception {
        final long entryId = ledgerHandle.addEntry(transformer.apply(data));
        return new Entry<>(getId(), entryId, data);
    }

    <T> Entry<T> consumeEntries(final long firstEntry,
                                final Entry<T> lastRecordedEntry,
                                final Consumer<Entry<T>> consumer,
                                final Function<byte[], T> transformer) throws Exception {
        return streamFrom(firstEntry).map(e -> new Entry<>(e.getLedgerId(),
                                                           e.getEntryId(),
                                                           transformer.apply(e.getEntry())))
                                     .peek(consumer)
                                     .reduce((previous, current) -> current)
                                     .orElse(lastRecordedEntry); // return the last recorded as a fallback reference
    }

    boolean isClosed() {
        return ledgerHandle.isClosed();
    }

    void close() throws Exception {
        ledgerHandle.close();
    }

    private Stream<LedgerEntry> streamFrom(final long entry) throws InterruptedException, BKException {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(ledgerHandle.readEntries(entry,
                                                                                                 getLastRecordedEntryId())
                                                                                    .asIterator(),
                                                                        Spliterator.ORDERED),
                                    false);
    }

    public record Entry<T>(long ledgerId, long entryId, T data) implements Comparable<Entry<T>> {
        private static final int NOT_EXISTS = -1;

        public static <T> Entry<T> initial() {
            return new Entry<>(NOT_EXISTS, NOT_EXISTS, null);
        }

        public boolean exists() {
            return entryId > NOT_EXISTS;
        }

        @Override
        public int compareTo(final Ledger.Entry<T> o) {
            return 0;
        }
    }

    public static class LedgerException extends RuntimeException {
        private final Entry<?> lastRecordedEntry;

        public LedgerException(final Entry<?> lastRecordedEntry) {
            this.lastRecordedEntry = lastRecordedEntry;
        }

        public <T> Entry<T> getLastRecordedEntry() {
            //noinspection unchecked
            return (Entry<T>) lastRecordedEntry;
        }
    }
}

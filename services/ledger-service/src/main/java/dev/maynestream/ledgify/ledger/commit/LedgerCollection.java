package dev.maynestream.ledgify.ledger.commit;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class LedgerCollection implements Iterable<Long> {

    static LedgerCollection fromBytes(byte[] bytes) {
        final LedgerCollection ledgerCollection = new LedgerCollection();
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        while (bb.remaining() > 0) {
            ledgerCollection.ledgerIds.add(bb.getLong());
        }
        return ledgerCollection;
    }

    // insertion order important
    private final List<Long> ledgerIds = new ArrayList<>();

    @Override
    public Iterator<Long> iterator() {
        return ledgerIds.iterator();
    }

    boolean isEmpty() {
        return ledgerIds.isEmpty();
    }

    void append(long ledgerId) {
        ledgerIds.add(ledgerId);
    }

    byte[] toBytes() {
        ByteBuffer bb = ByteBuffer.allocate((Long.SIZE * ledgerIds.size()) / 8);
        for (Long l : ledgerIds) {
            bb.putLong(l);
        }
        return bb.array();
    }

    LedgerCollection since(long ledgerId) {
        final LedgerCollection ledgerCollection = new LedgerCollection();
        final List<Long> idsSince = ledgerIds.subList(ledgerIds.indexOf(ledgerId) + 1, ledgerIds.size());
        ledgerCollection.ledgerIds.addAll(idsSince);
        return ledgerCollection;
    }
}

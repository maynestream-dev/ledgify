package dev.maynestream.ledgify.ledger.commit;

import dev.maynestream.ledgify.ledger.BookkeeperConfiguration;
import org.apache.bookkeeper.client.BookKeeper;

public class LedgerAccessor {
    private static final BookKeeper.DigestType DIGEST_TYPE = BookKeeper.DigestType.MAC;

    private final BookKeeper bookKeeper;
    private final BookkeeperConfiguration bookkeeperConfiguration;
    private final byte[] ledgerPassword;

    public LedgerAccessor(final BookKeeper bookKeeper,
                          final BookkeeperConfiguration bookkeeperConfiguration,
                          final byte[] ledgerPassword) {
        this.bookKeeper = bookKeeper;
        this.bookkeeperConfiguration = bookkeeperConfiguration;
        this.ledgerPassword = ledgerPassword;
    }

    Ledger create() throws Exception {
        return new Ledger(bookKeeper.createLedger(bookkeeperConfiguration.getDefaultEnsembleSize(),
                                                  bookkeeperConfiguration.getDefaultWriteQuorumSize(),
                                                  bookkeeperConfiguration.getDefaultAckQuorumSize(),
                                                  DIGEST_TYPE,
                                                  ledgerPassword));
    }

    Ledger openAsLeader(final Long entry) throws Exception {
        return new Ledger(bookKeeper.openLedger(entry,
                                                BookKeeper.DigestType.MAC,
                                                ledgerPassword));
    }

    Ledger openForRead(final long previous) throws Exception {
        return new Ledger(bookKeeper.openLedgerNoRecovery(previous,
                                                          BookKeeper.DigestType.MAC,
                                                          ledgerPassword));
    }
}

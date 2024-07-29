package dev.maynestream.ledgify.ledger.transaction;

import dev.maynestream.ledgify.ledger.BookkeeperConfiguration;
import dev.maynestream.ledgify.ledger.commit.Ledger.Entry;
import dev.maynestream.ledgify.ledger.commit.LedgerAccessor;
import dev.maynestream.ledgify.ledger.commit.LedgerCollectionStore;
import dev.maynestream.ledgify.ledger.commit.LedgerReader;
import dev.maynestream.ledgify.transaction.Transaction;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.bookkeeper.client.BookKeeper;
import org.apache.curator.framework.CuratorFramework;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

@Slf4j
public class TransactionReader extends LedgerReader<Transaction> implements Runnable {

    private static final String DAILY_LEDGER_PATH_FORMAT = "%s/%s";

    public TransactionReader(final BookKeeper bookKeeper,
                             final BookkeeperConfiguration bookkeeperConfiguration,
                             final CuratorFramework curator,
                             final UUID uniqueId,
                             final LocalDate date,
                             final Consumer<Entry<Transaction>> consumer) {
        super(new LedgerAccessor(bookKeeper, bookkeeperConfiguration, requireNonNull(uniqueId).toString().getBytes()),
              new LedgerCollectionStore(curator, DAILY_LEDGER_PATH_FORMAT.formatted(uniqueId, date)),
              consumer,
              TransactionReader::parse);
    }

    @Override
    @SneakyThrows
    public void run() {
        readAll(false);
    }

    @SneakyThrows
    private static Transaction parse(byte[] bytes) {
        return Transaction.parseFrom(bytes);
    }
}

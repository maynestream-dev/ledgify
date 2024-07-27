package dev.maynestream.ledgify.ledger.transaction;

import dev.maynestream.ledgify.ledger.BookkeeperConfiguration;
import dev.maynestream.ledgify.ledger.TestcontainersConfiguration;
import dev.maynestream.ledgify.transaction.Transaction;
import dev.maynestream.ledgify.transaction.TransactionCommitStatus;
import dev.maynestream.ledgify.transaction.TransactionTestFixtures;
import lombok.SneakyThrows;
import org.apache.bookkeeper.client.BookKeeper;
import org.apache.curator.framework.CuratorFramework;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static dev.maynestream.ledgify.CommonTestFixtures.randomId;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TransactionCommitterTest {

    @Autowired
    private BookKeeper bookkeeper;

    @Autowired
    private BookkeeperConfiguration bookkeeperConfiguration;

    @Autowired
    private CuratorFramework curator;

    @Test
    public void shouldMaintainOrderOfSerialCommits() {
        // given
        final UUID accountId = randomId();
        final int transactionCount = 10;
        final TransactionLog log = new TransactionLog(accountId);
        final List<Transaction> expected = Stream.generate(() -> pendingTransactionForAccount(accountId))
                                                 .limit(transactionCount)
                                                 .toList();

        // when
        try (final TransactionCommitter committer = getCommitter(accountId, log);
             final TransactionCommitter follower = getCommitter(accountId, log)) {
            new Thread(committer).start();
            new Thread(follower).start();
            expected.forEach(t -> submit(log, t));
        }
        await().atMost(Duration.ofSeconds(20)).until(() -> log.getCommits().size() == transactionCount);

        // then
        assertThat(log.getCommits(), contains(expected.toArray(new Transaction[0])));
    }

    private static Transaction pendingTransactionForAccount(final UUID accountId) {
        final Transaction.Builder transaction = TransactionTestFixtures.transaction();
        transaction.getDetailsBuilder().setDebitAccountId(accountId.toString()).build();
        transaction.getCommitStateBuilder().setContext("").setStatus(TransactionCommitStatus.PENDING);
        return transaction.build();
    }

    private TransactionCommitter getCommitter(final UUID accountId, final TransactionLog log) {
        return new TransactionCommitter(bookkeeper,
                                        bookkeeperConfiguration,
                                        curator,
                                        log,
                                        accountId,
                                        LocalDate.now());
    }

    @SneakyThrows
    private static void submit(final TransactionLog log, Transaction transaction) {
        log.submit(transaction);
    }
}
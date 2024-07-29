package dev.maynestream.ledgify.ledger.transaction;

import dev.maynestream.ledgify.ledger.BookkeeperConfiguration;
import dev.maynestream.ledgify.ledger.TestcontainersConfiguration;
import dev.maynestream.ledgify.ledger.commit.Ledger;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
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
        final List<Transaction> expected = generateTransactions(accountId, transactionCount);

        // when
        try (final TransactionCommitter committer = getCommitter(accountId, log);
             final TransactionCommitter follower = getCommitter(accountId, log)) {
            new Thread(committer).start();
            new Thread(follower).start();

            expected.forEach(t -> submit(log, t));

            await().atMost(Duration.ofSeconds(20)).until(() -> log.getCommits().size() == transactionCount);
        }

        // then
        assertThat(log.getCommits(), contains(expected.toArray(new Transaction[0])));
    }

    @Test
    public void shouldCorrectlyReadTransactionsAfterCommit() throws InterruptedException {
        // given
        final UUID accountId = randomId();
        final int transactionCount = 10;
        final TransactionLog log = new TransactionLog(accountId);
        final List<Transaction> expected = generateTransactions(accountId, transactionCount);
        final List<Transaction> actual = new ArrayList<>();

        // when
        try (final TransactionCommitter committer = getCommitter(accountId, log)) {
            final Thread thread = new Thread(committer);
            thread.start();

            expected.forEach(t -> submit(log, t));

            await().atMost(Duration.ofSeconds(20)).until(() -> log.getCommits().size() == transactionCount);
            thread.interrupt();
            thread.join();
        }

        final TransactionReader reader = getReader(accountId, e -> actual.add(e.data()));
        final Thread readerThread = new Thread(reader);
        readerThread.start();
        readerThread.join(Duration.ofSeconds(20));

        // then
        assertThat(actual, contains(expected.toArray(new Transaction[0])));
    }

    @Test
    public void shouldMaintainOrderOfSerialCommitsIfLeaderChangesDueToInterrupt() {
        // given
        final UUID accountId = randomId();
        final int transactionCount = 10;
        final TransactionLog log = new TransactionLog(accountId);
        final List<Transaction> expected = new ArrayList<>();

        // when
        try (final TransactionCommitter committer = getCommitter(accountId, log);
             final TransactionCommitter follower = getCommitter(accountId, log)) {
            final Thread committerThread = new Thread(committer);
            committerThread.start();
            new Thread(follower).start();
            generateTransactions(accountId, transactionCount).stream()
                                                             .peek(expected::add)
                                                             .forEach(t -> submit(log, t));

            await().atMost(Duration.ofSeconds(20)).until(() -> log.getCommits().size() == transactionCount);

            committerThread.interrupt();

            generateTransactions(accountId, transactionCount).stream()
                                                             .peek(expected::add)
                                                             .forEach(t -> submit(log, t));

            await().atMost(Duration.ofSeconds(20)).until(() -> log.getCommits().size() == transactionCount * 2);
        }

        // then
        assertThat(log.getCommits(), contains(expected.toArray(new Transaction[0])));
    }

    private static List<Transaction> generateTransactions(final UUID accountId, final int transactionCount) {
        return Stream.generate(() -> pendingTransactionForAccount(accountId))
                     .limit(transactionCount)
                     .toList();
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

    private TransactionReader getReader(final UUID accountId, final Consumer<Ledger.Entry<Transaction>> consumer) {
        return new TransactionReader(bookkeeper,
                                     bookkeeperConfiguration,
                                     curator,
                                     accountId,
                                     LocalDate.now(),
                                     consumer);
    }

    @SneakyThrows
    private static void submit(final TransactionLog log, Transaction transaction) {
        log.submit(transaction);
    }
}
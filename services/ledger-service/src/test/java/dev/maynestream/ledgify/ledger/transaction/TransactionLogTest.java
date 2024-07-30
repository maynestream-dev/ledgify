package dev.maynestream.ledgify.ledger.transaction;

import dev.maynestream.ledgify.ledger.transaction.TransactionLog.CommitAttempt;
import dev.maynestream.ledgify.transaction.Transaction;
import dev.maynestream.ledgify.transaction.TransactionCommitState;
import dev.maynestream.ledgify.transaction.TransactionCommitStatus;
import dev.maynestream.ledgify.transaction.TransactionTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.util.UUID;

import static dev.maynestream.ledgify.ledger.transaction.TransactionLog.COMMIT_TRANSACTION_TIMEOUT_SECS;
import static dev.maynestream.ledgify.ledger.transaction.TransactionLog.SUBMIT_TRANSACTION_TIMEOUT_SECS;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionLogTest {

    @Test
    void shouldThrowExceptionWhenInstantiatedWithNullAccount() {
        // given
        final UUID accountId = null;

        // when
        final Executable init = () -> new TransactionLog(accountId);

        // then
        assertThrows(NullPointerException.class, init);
    }

    @Test
    void shouldThrowExceptionWhenTransactionIsNull() {
        // given
        final UUID accountId = UUID.randomUUID();
        final TransactionLog log = new TransactionLog(accountId);

        // when
        final Executable submit = () -> log.submit(CommitAttempt.forTransaction(null));

        // then
        assertThrows(IllegalArgumentException.class, submit);
    }

    @Test
    void shouldThrowExceptionWhenTransactionIsForWrongAccount() {
        // given
        final UUID accountId = UUID.randomUUID();
        final TransactionLog log = new TransactionLog(accountId);
        final Transaction transaction = transactionForDifferentAccounts().build();

        // when
        final Executable submit = () -> log.submit(CommitAttempt.forTransaction(transaction));

        // then
        assertThrows(IllegalArgumentException.class, submit);
    }

    @Test
    void shouldThrowExceptionWhenAwaitSubmitInterrupted() {
        // given
        final UUID accountId = UUID.randomUUID();
        final TransactionLog log = new TransactionLog(accountId);
        final Transaction transaction = transactionForAccountDebit(accountId).build();

        // when
        final Executable submit = () -> {
            final Thread currentThread = Thread.currentThread();
            doConcurrentlyWithDelay(ofSeconds(1), currentThread::interrupt);

            log.submit(CommitAttempt.forTransaction(transaction));
        };

        // then
        assertThrows(InterruptedException.class, submit);
    }

    @Test
    void shouldThrowExceptionWhenAwaitCommitInterrupted() {
        // given
        final UUID accountId = UUID.randomUUID();
        final TransactionLog log = new TransactionLog(accountId);
        final Transaction transaction = transactionForAccountDebit(accountId).build();

        // when
        final Executable submit = () -> {
            doConcurrentlyWithDelay(ofSeconds(1), () -> {
                log.awaitCommit(t -> {
                    // simulate long commit time
                    Thread.sleep(ofSeconds(COMMIT_TRANSACTION_TIMEOUT_SECS + 1));
                });
            });

            final Thread currentThread = Thread.currentThread();
            doConcurrentlyWithDelay(ofSeconds(SUBMIT_TRANSACTION_TIMEOUT_SECS - 3), currentThread::interrupt);

            log.submit(CommitAttempt.forTransaction(transaction));
        };

        // then
        assertThrows(InterruptedException.class, submit);
    }

    @Test
    void shouldReturnFailedStatusWhenSubmitNotHandledWithinTimeout() throws InterruptedException {
        // given
        final UUID accountId = UUID.randomUUID();
        final TransactionLog log = new TransactionLog(accountId);
        final Transaction transaction = transactionForAccountDebit(accountId).build();

        // when
        final TransactionCommitState state = log.submit(CommitAttempt.forTransaction(transaction));

        assertThat(state.getStatus(), equalTo(TransactionCommitStatus.FAILED));
    }

    @Test
    void shouldReturnUnknownStatusWhenAwaitCommitNotHandledWithinTimeout() throws InterruptedException {
        // given
        final UUID accountId = UUID.randomUUID();
        final TransactionLog log = new TransactionLog(accountId);
        final Transaction transaction = transactionForAccountDebit(accountId).build();

        // when
        doConcurrentlyWithDelay(ofSeconds(0), () -> {
            log.awaitCommit(t -> {
                // simulate long commit time
                Thread.sleep(ofSeconds(COMMIT_TRANSACTION_TIMEOUT_SECS + 1));
            });
        });

        final TransactionCommitState state = log.submit(CommitAttempt.forTransaction(transaction));

        assertThat(state.getStatus(), equalTo(TransactionCommitStatus.UNKNOWN));
    }

    @Test
    void shouldReturnCompletedStatusWhenAwaitCommitHandled() throws InterruptedException {
        // given
        final UUID accountId = UUID.randomUUID();
        final TransactionLog log = new TransactionLog(accountId);
        final Transaction transaction = transactionForAccountDebit(accountId).build();

        // when
        doConcurrentlyWithDelay(ofSeconds(0), () -> {
            log.awaitCommit(t -> { });
        });

        final TransactionCommitState state = log.submit(CommitAttempt.forTransaction(transaction));

        assertThat(state.getStatus(), equalTo(TransactionCommitStatus.COMPLETED));
    }

    @Test
    void shouldThrowExceptionWhenAwaitTransactionInterrupted() {
        // given
        final UUID accountId = UUID.randomUUID();
        final TransactionLog log = new TransactionLog(accountId);

        // when
        final Executable await = () -> {
            final Thread currentThread = Thread.currentThread();
            doConcurrentlyWithDelay(ofMillis(500), currentThread::interrupt);

            log.awaitCommit(transaction -> { });
        };

        // then
        assertThrows(InterruptedException.class, await);
    }

    private static Transaction.Builder transactionForAccountDebit(final UUID accountId) {
        return TransactionTestFixtures.transaction()
                                      .mergeDetails(TransactionTestFixtures.transactionDetails()
                                                                           .setDebitAccountId(accountId.toString())
                                                                           .build());
    }

    private static Transaction.Builder transactionForDifferentAccounts() {
        return TransactionTestFixtures.transaction();
    }

    private static void doConcurrentlyWithDelay(Duration delay, Action action) {
        new Thread(() -> {
            try {
                Thread.sleep(delay);

                action.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    interface Action {
        void run() throws Exception;
    }
}
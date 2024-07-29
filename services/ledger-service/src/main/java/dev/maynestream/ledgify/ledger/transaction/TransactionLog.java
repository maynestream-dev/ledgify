package dev.maynestream.ledgify.ledger.transaction;

import dev.maynestream.ledgify.ledger.transaction.logging.TransactionLoggingContext;
import dev.maynestream.ledgify.transaction.Transaction;
import dev.maynestream.ledgify.transaction.TransactionCommitState;
import dev.maynestream.ledgify.transaction.TransactionCommitStatus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import static dev.maynestream.ledgify.transaction.TransactionCommitStatus.COMPLETED;
import static dev.maynestream.ledgify.transaction.TransactionCommitStatus.FAILED;
import static dev.maynestream.ledgify.transaction.TransactionCommitStatus.UNKNOWN;

@Slf4j
public class TransactionLog {
    public static final int SUBMIT_TRANSACTION_TIMEOUT_SECS = 5;
    public static final int COMMIT_TRANSACTION_TIMEOUT_SECS = 10;
    public static final int AWAIT_TRANSACTION_TIMEOUT_SECS = 1;

    private final SynchronousQueue<Transaction> submitted = new SynchronousQueue<>(true);
    private final CountDownLatch latch = new CountDownLatch(1);

    private final ConcurrentHashMap<Transaction, Long> commits = new ConcurrentHashMap<>();

    @Getter
    private final UUID accountId;

    public TransactionLog(final UUID accountId) {
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
    }

    public Set<Transaction> getCommits() {
        final Set<Transaction> transactions = new TreeSet<>((o1, o2) -> commits.get(o1).compareTo(commits.get(o2)));
        transactions.addAll(commits.keySet());
        return transactions;
    }

    TransactionCommitState submit(final Transaction transaction) throws InterruptedException {
        validateTransaction(transaction);

        try (final var ignore = TransactionLoggingContext.account(accountId).transaction(transaction.getTransactionId())) {
            log.info("Submitting transaction {}", transaction.getTransactionId());
            if (submitted.offer(transaction, SUBMIT_TRANSACTION_TIMEOUT_SECS, TimeUnit.SECONDS)) {
                log.info("Awaiting commit of submitted transaction {}", transaction.getTransactionId());
                if (latch.await(COMMIT_TRANSACTION_TIMEOUT_SECS, TimeUnit.SECONDS)) {
                    log.warn("Committed transaction {}", transaction.getTransactionId());
                    return state(transaction, COMPLETED, "Transaction successfully committed");
                } else {
                    log.info("Commit timeout for transaction {}", transaction.getTransactionId());
                    return state(transaction, UNKNOWN, "Transaction took too long to commit");
                }
            } else {
                log.warn("Submit timeout for transaction {}", transaction.getTransactionId());
                return state(transaction, FAILED, "Failed to submit transaction - no committers available");
            }
        } catch (InterruptedException e) {
            log.warn("Submit interrupted for transaction {}", transaction.getTransactionId());
            throw e;
        } catch (Exception e) {
            log.error("Exception during submit for transaction {}", transaction.getTransactionId());
            throw e;
        }
    }

    void awaitCommit(CommitAction consumer) throws Exception {
        try (var ignoreAcc = TransactionLoggingContext.account(accountId)) {
            log.info("Awaiting transaction...");
            final Transaction transaction = submitted.poll(AWAIT_TRANSACTION_TIMEOUT_SECS, TimeUnit.SECONDS);

            if (transaction != null) {
                try (var ignoreTx = ignoreAcc.transaction(transaction.getTransactionId())) {
                    consumer.commit(transaction);
                    commits.put(transaction, System.currentTimeMillis());
                    latch.countDown();
                }
            }
        }
    }

    private void validateTransaction(final Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("transaction cannot be null");
        } else if (!accountId.equals(UUID.fromString(transaction.getDetails()
                                                                .getDebitAccountId())) && !accountId.equals(UUID.fromString(
                transaction.getDetails().getCreditAccountId()))) {
            throw new IllegalArgumentException("transaction submitted for incorrect account");
        }
    }

    private static TransactionCommitState state(final Transaction transaction,
                                                final TransactionCommitStatus status,
                                                final String stateContext) {
        final TransactionCommitState state = TransactionCommitState.newBuilder()
                                                                   .setStatus(status)
                                                                   .setContext(stateContext)
                                                                   .build();
        transaction.toBuilder().setCommitState(state);
        return state;
    }

    interface CommitAction {
        void commit(Transaction transaction) throws Exception;
    }
}

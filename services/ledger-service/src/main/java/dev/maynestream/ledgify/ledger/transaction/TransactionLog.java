package dev.maynestream.ledgify.ledger.transaction;

import dev.maynestream.ledgify.ledger.transaction.logging.TransactionLoggingContext;
import dev.maynestream.ledgify.transaction.Transaction;
import dev.maynestream.ledgify.transaction.TransactionCommitState;
import dev.maynestream.ledgify.transaction.TransactionCommitStatus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static dev.maynestream.ledgify.transaction.TransactionCommitStatus.COMPLETED;
import static dev.maynestream.ledgify.transaction.TransactionCommitStatus.FAILED;
import static dev.maynestream.ledgify.transaction.TransactionCommitStatus.UNKNOWN;

@Slf4j
public class TransactionLog {
    public static final int SUBMIT_TRANSACTION_TIMEOUT_SECS = 5;
    public static final int COMMIT_TRANSACTION_TIMEOUT_SECS = 10;
    public static final int AWAIT_TRANSACTION_TIMEOUT_SECS = 1;

    private final SynchronousQueue<CommitAttempt> submitted = new SynchronousQueue<>(true);
    private final CountDownLatch latch = new CountDownLatch(1);

    private final ConcurrentHashMap<Transaction, Long> commits = new ConcurrentHashMap<>();

    @Getter
    private final UUID accountId;

    public TransactionLog(final UUID accountId) {
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
    }

    public SortedSet<Transaction> getCommits() {
        return sort(commits);
    }

    TransactionCommitState submit(final CommitAttempt commitAttempt) throws InterruptedException {
        final Transaction transaction = commitAttempt.transaction;

        validateTransaction(transaction);

        try (final var ignore = TransactionLoggingContext.account(accountId).transaction(transaction.getTransactionId())) {
            try {
                log.info("Submitting transaction...");
                if (submitted.offer(commitAttempt, SUBMIT_TRANSACTION_TIMEOUT_SECS, TimeUnit.SECONDS)) {
                    log.info("Awaiting commit of submitted transaction...");
                    if (latch.await(COMMIT_TRANSACTION_TIMEOUT_SECS, TimeUnit.SECONDS)) {
                        log.info("Transaction committed");
                        return state(transaction, COMPLETED, "Transaction successfully committed");
                    } else {
                        log.info("Commit timeout for transaction");
                        return state(transaction, UNKNOWN, "Transaction took too long to commit");
                    }
                } else {
                    log.warn("Submit timeout for transaction");
                    return state(transaction, FAILED, "Failed to submit transaction - no committers available");
                }
            } catch (InterruptedException e) {
                log.warn("Submit interrupted for transaction");
                throw e;
            } catch (Exception e) {
                log.error("Exception during submit for transaction");
                throw e;
            }
        }
    }

    void awaitCommit(CommitAction consumer) throws Exception {
        try (var ignoreAcc = TransactionLoggingContext.account(accountId)) {
            log.debug("Awaiting transaction...");
            final CommitAttempt commitAttempt = submitted.poll(AWAIT_TRANSACTION_TIMEOUT_SECS, TimeUnit.SECONDS);

            if (commitAttempt != null) {
                validateTransaction(commitAttempt.transaction);

                try (var ignoreTx = ignoreAcc.transaction(commitAttempt.transaction.getTransactionId())) {
                    consumer.commit(commitAttempt);
                    commits.put(commitAttempt.transaction, System.currentTimeMillis());
                    latch.countDown();
                }
            }
        }
    }

    private void validateTransaction(final Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("transaction cannot be null");
        } else if (!accountId.equals(UUID.fromString(transaction.getDetails()
                                                                .getDebitAccountId()))
                && !accountId.equals(UUID.fromString(transaction.getDetails()
                                                                .getCreditAccountId()))) {
            throw new IllegalArgumentException("transaction submitted for incorrect account");
        } else if (commits.keySet().stream().anyMatch(t -> t.getTransactionId().equals(transaction.getTransactionId()))) {
            throw new IllegalArgumentException("transaction has already been submitted");
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
        void commit(CommitAttempt commitAttempt) throws Exception;
    }

    public record CommitAttempt(Transaction transaction, AtomicLong entryId) {
        static CommitAttempt forTransaction(final Transaction transaction) {
            return new CommitAttempt(transaction, new AtomicLong(0));
        }
    }

    public static SortedSet<Transaction> sort(final ConcurrentHashMap<Transaction, Long> commits) {
        final SortedSet<Transaction> transactions = new TreeSet<>(Comparator.comparing(commits::get));
        transactions.addAll(commits.keySet());
        return transactions;
    }
}

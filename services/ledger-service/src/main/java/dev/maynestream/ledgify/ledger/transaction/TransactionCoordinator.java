package dev.maynestream.ledgify.ledger.transaction;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import dev.maynestream.ledgify.ledger.LedgerCommitResponse;
import dev.maynestream.ledgify.transaction.Transaction;
import dev.maynestream.ledgify.transaction.TransactionCommitState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Closeable;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.SortedSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Slf4j
@Service
public class TransactionCoordinator implements Closeable {

    static {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log.error("Uncaught exception in thread {}", t.getName(), e);
        });
    }

    public static final int DEFAULT_RESILIENCE_FACTOR = 3;

    private final LoadingCache<UUID, TransactionHandler> accountHandlers = CacheBuilder.newBuilder()
//                                                                                       .expireAfterAccess(1, TimeUnit.SECONDS)
                                                                                       .removalListener((RemovalListener<UUID, TransactionHandler>) notification -> {
                                                                                           notification.getValue().close();
                                                                                       })
                                                                                       .build(new TransactionHandlerLoader());

    private final TransactionCommitterFactory committerFactory;

    public TransactionCoordinator(final TransactionCommitterFactory committerFactory) {
        this.committerFactory = committerFactory;
    }

    public LedgerCommitResponse routeTransaction(final UUID accountId, final Transaction transaction) throws InterruptedException, ExecutionException {
        return accountHandlers.get(accountId).handle(transaction);
    }

    public SortedSet<Transaction> listTransactions(final UUID accountId) throws InterruptedException, ExecutionException {
        final TransactionHandler handler = accountHandlers.getIfPresent(accountId);

        if (handler != null) {
            return handler.log.getCommits();
        }

        final ConcurrentHashMap<Transaction, Long> commits = new ConcurrentHashMap<>();
        final TransactionReader reader = committerFactory.createReader(accountId,
                                                                       LocalDate.now(),
                                                                       e -> commits.put(e.data(), e.entryId()));
        final Thread readerThread = new Thread(reader);
        readerThread.start();
        readerThread.join(Duration.ofSeconds(20));
        return TransactionLog.sort(commits);
    }

    private TransactionHandler createHandler(final UUID uuid) {
        return new TransactionHandler(uuid, LocalDate.now(), committerFactory, DEFAULT_RESILIENCE_FACTOR);
    }

    @Override
    public void close() {
        accountHandlers.invalidateAll();
    }

    static class TransactionHandler implements Closeable {
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        private final TransactionLog log;
        private final Collection<TransactionCommitter> committers;

        TransactionHandler(final UUID accountId,
                           final LocalDate date,
                           final TransactionCommitterFactory committerFactory,
                           final int resilienceFactor) {
            this.log = new TransactionLog(accountId);

            if (resilienceFactor < 1) {
                throw new IllegalArgumentException("Resilience factor must be greater than 0");
            }

            this.committers = Stream.generate(() -> committerFactory.create(log, accountId, date))
                                    .limit(resilienceFactor)
                                    .toList();

            this.committers.forEach(executor::submit);
        }

        synchronized LedgerCommitResponse handle(final Transaction transaction) throws InterruptedException {
            final AtomicLong entryId = new AtomicLong(0);
            final TransactionCommitState result = log.submit(new TransactionLog.CommitAttempt(transaction, entryId));
            return LedgerCommitResponse.newBuilder().setEntryId(entryId.get()).setState(result).build();
        }

        @Override
        public synchronized void close() {
            for (TransactionCommitter committer : committers) {
                try {
                    committer.close();
                } catch (Exception ignored) {
                }
            }
            try {
                executor.shutdownNow();
            } catch (Exception ignored) {
            }
        }
    }

    private class TransactionHandlerLoader extends CacheLoader<UUID, TransactionHandler> {
        @Override
        public TransactionHandler load(final UUID key) {
            return createHandler(key);
        }
    }
}

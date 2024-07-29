package dev.maynestream.ledgify.ledger.commit.logging;

import lombok.SneakyThrows;
import org.slf4j.MDC;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LedgerLoggingContext implements AutoCloseable {
    public static final String LEDGER_PARTICIPANT_ROLE = "ledger-participant-role";
    public static final String LEDGER_PARTICIPANT_ID = "ledger-participant-id";

    public static LedgerLoggingContext ledger(final boolean leader, final UUID uniqueId) {
        final LedgerLoggingContext context = new LedgerLoggingContext();
        context.addCloseable(MDC.putCloseable(LEDGER_PARTICIPANT_ROLE, leader ? "leader" : "follower"));
        context.addCloseable(MDC.putCloseable(LEDGER_PARTICIPANT_ID, uniqueId.toString()));
        return context;
    }

    private final List<Closeable> closeables = new ArrayList<>();

    protected LedgerLoggingContext() {}

    protected void addCloseable(final MDC.MDCCloseable closeable) {
        closeables.add(closeable);
    }

    @Override
    @SneakyThrows
    public void close() {
        for (Closeable closeable : closeables) {
            closeable.close();
        }
    }
}

package dev.maynestream.ledgify.ledger.transaction.logging;

import dev.maynestream.ledgify.ledger.commit.logging.LedgerLoggingContext;
import org.slf4j.MDC;

import java.util.UUID;

public class TransactionLoggingContext extends LedgerLoggingContext {
    public static final String ACCOUNT_ID = "account-id";
    public static final String TRANSACTION_ID = "transaction-id";

    public static TransactionLoggingContext account(final UUID accountId) {
        final TransactionLoggingContext context = new TransactionLoggingContext();
        context.addCloseable(MDC.putCloseable(ACCOUNT_ID, accountId.toString()));
        return context;
    }

    public TransactionLoggingContext transaction(final String transactionId) {
        addCloseable(MDC.putCloseable(TRANSACTION_ID, transactionId));
        return this;
    }
}

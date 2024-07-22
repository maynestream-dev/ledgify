package dev.maynestream.ledgify.transaction;

import dev.maynestream.ledgify.domain.tables.Transaction;
import dev.maynestream.ledgify.domain.tables.records.TransactionRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Currency;
import java.util.UUID;

@Component
public class TransactionRepository {

    private final DSLContext dsl;

    public TransactionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public TransactionRecord submitTransaction(String description, UUID debitAccountId, UUID creditAccountId, Integer debitLedgerEntryId, Integer creditLedgerEntryId, Currency currency, BigDecimal amount) {
        return dsl.insertInto(Transaction.TRANSACTION)
                  .set(Transaction.TRANSACTION.DESCRIPTION, description)
                  .set(Transaction.TRANSACTION.DEBIT_ACCOUNT_ID, debitAccountId)
                  .set(Transaction.TRANSACTION.CREDIT_ACCOUNT_ID, creditAccountId)
                  .set(Transaction.TRANSACTION.DEBIT_LEDGER_ENTRY_ID, debitLedgerEntryId)
                  .set(Transaction.TRANSACTION.CREDIT_LEDGER_ENTRY_ID, creditLedgerEntryId)
                  .set(Transaction.TRANSACTION.CURRENCY, currency.getCurrencyCode())
                  .set(Transaction.TRANSACTION.AMOUNT, amount)
                  .returning()
                  .fetchOne();
    }

    public TransactionRecord updateTransactionState(UUID transactionId, String state, String stateContext) {
        return dsl.update(Transaction.TRANSACTION)
                  .set(Transaction.TRANSACTION.STATE, state)
                  .set(Transaction.TRANSACTION.STATE_CONTEXT, stateContext)
                  .where(Transaction.TRANSACTION.ID.eq(transactionId))
                  .returning()
                  .fetchOne();
    }

    public Collection<TransactionRecord> listTransactions() {
        return dsl.select()
                  .from(Transaction.TRANSACTION)
                  .fetch()
                  .into(TransactionRecord.class);
    }
}

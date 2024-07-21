package dev.maynestream.ledgify.account;

import dev.maynestream.ledgify.account.tables.Account;
import dev.maynestream.ledgify.account.tables.records.AccountRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.UUID;

@Component
public class AccountRepository {

    private final DSLContext dsl;

    public AccountRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public AccountRecord createAccount(UUID customerId, Integer ledgerId, Currency currency) {
        return dsl.insertInto(Account.ACCOUNT)
                .set(Account.ACCOUNT.CUSTOMER_ID, customerId)
                .set(Account.ACCOUNT.LEDGER_ID, ledgerId)
                .set(Account.ACCOUNT.CURRENCY, currency.getCurrencyCode())
                .set(Account.ACCOUNT.BALANCE, BigDecimal.ZERO)
                .set(Account.ACCOUNT.CREATED, LocalDateTime.now())
                .set(Account.ACCOUNT.UPDATED, LocalDateTime.now())
                .returning()
                .fetchOne();
    }

    public AccountRecord creditAccount(UUID accountId, BigDecimal amount) {
        return dsl.update(Account.ACCOUNT)
                .set(Account.ACCOUNT.BALANCE, Account.ACCOUNT.BALANCE.plus(amount))
                .where(Account.ACCOUNT.ID.eq(accountId))
                .returning()
                .fetchSingle();
    }

    public AccountRecord debitAccount(UUID accountId, BigDecimal amount) {
        return dsl.update(Account.ACCOUNT)
                .set(Account.ACCOUNT.BALANCE, Account.ACCOUNT.BALANCE.minus(amount))
                .where(Account.ACCOUNT.ID.eq(accountId))
                .returning()
                .fetchSingle();
    }
}

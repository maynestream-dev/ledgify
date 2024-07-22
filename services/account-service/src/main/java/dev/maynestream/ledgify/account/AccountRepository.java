package dev.maynestream.ledgify.account;

import dev.maynestream.ledgify.domain.tables.Account;
import dev.maynestream.ledgify.domain.tables.records.AccountRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
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
                .returning()
                .fetchOne();
    }

    public Collection<AccountRecord> listAccounts(UUID customerId) {
        return dsl.select()
                .from(Account.ACCOUNT)
                .where(Account.ACCOUNT.CUSTOMER_ID.eq(customerId))
                .fetch()
                .into(AccountRecord.class);
    }
}

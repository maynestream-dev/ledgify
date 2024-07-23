CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE account
(
    id                UUID       NOT NULL PRIMARY KEY DEFAULT uuid_generate_v1(),
    customer_id       UUID       NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    available_balance NUMERIC    NOT NULL             DEFAULT 0,
    ledger_balance    NUMERIC    NOT NULL             DEFAULT 0,
    created           TIMESTAMP  NOT NULL             DEFAULT now(),
    updated           TIMESTAMP  NOT NULL             DEFAULT now(),
    version           INTEGER    NOT NULL             DEFAULT 1,

    CONSTRAINT customer_currency_unique UNIQUE (customer_id, currency)
);

CREATE TABLE transaction
(
    id                     UUID           NOT NULL PRIMARY KEY DEFAULT uuid_generate_v1(),
    description            VARCHAR(1000),
    debit_account_id       UUID           NOT NULL,
    credit_account_id      UUID           NOT NULL,
    currency               VARCHAR(3)     NOT NULL,
    amount                 numeric(15, 6) NOT NULL CHECK (amount > 0),
    state                  VARCHAR(20)    NOT NULL             DEFAULT 'PENDING' CHECK (state IN ('PENDING', 'COMPLETED', 'FAILED', 'UNKNOWN')),
    state_context          VARCHAR(1000),
    created                TIMESTAMP      NOT NULL             DEFAULT now(),
    updated                TIMESTAMP      NOT NULL             DEFAULT now(),
    version                INTEGER        NOT NULL             DEFAULT 1,

    CONSTRAINT fk_debit_account_id FOREIGN KEY (debit_account_id) REFERENCES account (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_credit_account_id FOREIGN KEY (credit_account_id) REFERENCES account (id)
        ON DELETE RESTRICT
);

CREATE FUNCTION on_entity_update()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated
        = now();
    NEW.version
        = OLD.version + 1;
    RETURN NEW;
END;
$$
    language 'plpgsql';

CREATE TRIGGER account_updated
    BEFORE UPDATE
    ON
        account
    FOR EACH ROW
EXECUTE PROCEDURE on_entity_update();

CREATE TRIGGER transaction_updated
    BEFORE UPDATE
    ON
        transaction
    FOR EACH ROW
EXECUTE PROCEDURE on_entity_update();

CREATE FUNCTION check_transaction_submission()
    RETURNS TRIGGER AS
$$
BEGIN
    IF (SELECT currency FROM account WHERE account.id = NEW.debit_account_id) <> NEW.currency THEN
        RAISE EXCEPTION 'debit account currency is not %', NEW.currency USING ERRCODE = 23514;
    END IF;

    IF (SELECT currency FROM account WHERE account.id = NEW.credit_account_id) <> NEW.currency THEN
        RAISE EXCEPTION 'credit account currency is not %', NEW.currency USING ERRCODE = 23514;
    END IF;

    IF NEW.debit_account_id = NEW.credit_account_id THEN
        RAISE EXCEPTION 'cannot debit and credit the same account' USING ERRCODE = 23514;
    END IF;
    RETURN NEW;
END;
$$
    language 'plpgsql';

CREATE TRIGGER transaction_submission
    BEFORE INSERT
    ON
        transaction
    FOR EACH ROW
EXECUTE PROCEDURE check_transaction_submission();

CREATE FUNCTION on_attempt_modify_complete_transaction()
    RETURNS TRIGGER AS
$$
BEGIN
    RAISE EXCEPTION 'cannot modify a complete or failed transaction' USING ERRCODE = 23514;
END;
$$
    language 'plpgsql';

CREATE TRIGGER transaction_finalised
    BEFORE UPDATE
    ON
        transaction
    FOR EACH ROW
    WHEN (OLD.state IN ('COMPLETED', 'FAILED'))
EXECUTE PROCEDURE on_attempt_modify_complete_transaction();

CREATE FUNCTION update_available_balances()
    RETURNS TRIGGER AS
$$
BEGIN
    UPDATE account
    SET available_balance = available_balance - OLD.amount
    WHERE account.id = OLD.debit_account_id;

    UPDATE account
    SET available_balance = available_balance + OLD.amount
    WHERE account.id = OLD.credit_account_id;

    RETURN NEW;
END;
$$
    language 'plpgsql';

CREATE TRIGGER transaction_submitted
    AFTER INSERT
    ON
        transaction
    FOR EACH ROW
EXECUTE PROCEDURE update_available_balances();

CREATE FUNCTION update_ledger_balances()
    RETURNS TRIGGER AS
$$
BEGIN
    UPDATE account
    SET ledger_balance = ledger_balance - OLD.amount
    WHERE account.id = OLD.debit_account_id;

    UPDATE account
    SET ledger_balance = ledger_balance + OLD.amount
    WHERE account.id = OLD.credit_account_id;

    RETURN NEW;
END;
$$
    language 'plpgsql';

CREATE TRIGGER transaction_completed
    AFTER UPDATE
    ON
        transaction
    FOR EACH ROW
    WHEN (OLD.state IS DISTINCT FROM NEW.state AND NEW.state = 'COMPLETED')
EXECUTE PROCEDURE update_ledger_balances();
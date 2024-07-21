CREATE
EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE account (
    id          UUID       NOT NULL PRIMARY KEY DEFAULT uuid_generate_v1(),
    customer_id UUID       NOT NULL,
    ledger_id   INTEGER    NOT NULL,
    currency    VARCHAR(3) NOT NULL,
    balance     NUMERIC    NOT NULL,
    created     TIMESTAMP  NOT NULL,
    updated     TIMESTAMP  NOT NULL,

    CONSTRAINT customer_currency_unique UNIQUE (customer_id, currency)
);
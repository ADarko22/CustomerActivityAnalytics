-- Phase 2: customers, transactions, and the three polymorphic activity tables.

CREATE TABLE customers (
    customer_id UUID PRIMARY KEY,
    first_name  VARCHAR(255) NOT NULL,
    last_name   VARCHAR(255) NOT NULL
);

CREATE TABLE transactions (
    transaction_id UUID PRIMARY KEY,
    customer_id    UUID NOT NULL REFERENCES customers (customer_id),
    activity_type  VARCHAR(20) NOT NULL CHECK (activity_type IN ('CARD', 'PAYMENT', 'CRYPTO')),
    amount         DECIMAL(18, 2) NOT NULL,
    currency       VARCHAR(10) NOT NULL,
    status         VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED', 'PENDING', 'FAILED', 'REVERSED')),
    created_at     TIMESTAMP NOT NULL
);

CREATE INDEX idx_transactions_customer_id ON transactions (customer_id);
CREATE INDEX idx_transactions_created_at ON transactions (created_at);

CREATE TABLE card_activity (
    transaction_id     UUID PRIMARY KEY REFERENCES transactions (transaction_id),
    card_pan           VARCHAR(32) NOT NULL,
    card_type          VARCHAR(20) NOT NULL,
    merchant_name      VARCHAR(255) NOT NULL,
    mcc_code           VARCHAR(4) NOT NULL,
    card_present       BOOLEAN NOT NULL,
    authorization_code VARCHAR(50),
    decline_reason     VARCHAR(255)
);

CREATE TABLE payment_activity (
    transaction_id        UUID PRIMARY KEY REFERENCES transactions (transaction_id),
    payment_method        VARCHAR(20) NOT NULL,
    sender_account        VARCHAR(64) NOT NULL,
    receiver_account      VARCHAR(64) NOT NULL,
    receiver_bank_country CHAR(2) NOT NULL
);

CREATE TABLE crypto_activity (
    transaction_id      UUID PRIMARY KEY REFERENCES transactions (transaction_id),
    blockchain          VARCHAR(50) NOT NULL,
    wallet_address_from VARCHAR(255) NOT NULL,
    wallet_address_to   VARCHAR(255) NOT NULL,
    tx_hash             VARCHAR(255) NOT NULL,
    exchange_name       VARCHAR(255)
);

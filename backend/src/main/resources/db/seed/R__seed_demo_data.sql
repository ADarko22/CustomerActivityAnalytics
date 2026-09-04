-- Local-only demo data (loaded when spring.flyway.locations includes this folder, i.e. the "local" profile).
-- Repeatable migration: reruns whenever its checksum changes, so it's safe to tweak during local development.

DELETE FROM risk_rules;
DELETE FROM card_activity;
DELETE FROM payment_activity;
DELETE FROM crypto_activity;
DELETE FROM transactions;
DELETE FROM customers;

INSERT INTO customers (customer_id, first_name, last_name)
VALUES ('11111111-1111-1111-1111-111111111111', 'Angelo', 'Buono'),
       ('22222222-2222-2222-2222-222222222222', 'Maria', 'Rossi'),
       ('33333333-3333-3333-3333-333333333333', 'John', 'Smith');

-- Angelo: 25 CARD transactions, enough to exercise pagination (default page size 20).
INSERT INTO transactions (transaction_id, customer_id, activity_type, amount, currency, status, created_at)
SELECT ('a0000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
       '11111111-1111-1111-1111-111111111111',
       'CARD',
       (50 + gs * 3.25)::decimal(18, 2),
       'EUR',
       (ARRAY ['COMPLETED', 'PENDING', 'FAILED', 'REVERSED'])[1 + (gs % 4)],
       TIMESTAMP '2026-01-01 00:00:00' + (gs || ' hours')::interval
FROM generate_series(1, 25) AS gs;

INSERT INTO card_activity (transaction_id, card_pan, card_type, merchant_name, mcc_code, card_present,
                            authorization_code, decline_reason)
SELECT ('a0000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
       '****' || lpad((1000 + gs)::text, 4, '0'),
       (ARRAY ['DEBIT', 'CREDIT', 'PREPAID'])[1 + (gs % 3)],
       (ARRAY ['Amazon', 'Starbucks', 'Uber', 'Carrefour', 'Apple Store'])[1 + (gs % 5)],
       lpad((5000 + gs)::text, 4, '0'),
       (gs % 2 = 0),
       'AUTH' || gs,
       CASE WHEN gs % 4 = 3 THEN 'Insufficient funds' ELSE NULL END
FROM generate_series(1, 25) AS gs;

-- Angelo: a few PAYMENT + CRYPTO rows too, so the activity-type filter has something to switch to.
INSERT INTO transactions (transaction_id, customer_id, activity_type, amount, currency, status, created_at)
SELECT ('b0000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
       '11111111-1111-1111-1111-111111111111',
       'PAYMENT',
       (500 + gs * 42.5)::decimal(18, 2),
       'EUR',
       (ARRAY ['COMPLETED', 'PENDING', 'FAILED'])[1 + (gs % 3)],
       TIMESTAMP '2026-02-01 00:00:00' + (gs || ' days')::interval
FROM generate_series(1, 3) AS gs;

INSERT INTO payment_activity (transaction_id, payment_method, sender_account, receiver_account,
                               receiver_bank_country)
SELECT ('b0000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
       (ARRAY ['ACH', 'WIRE', 'SWIFT'])[1 + (gs % 3)],
       'IT' || lpad((1000000 + gs)::text, 20, '0'),
       'DE' || lpad((2000000 + gs)::text, 20, '0'),
       (ARRAY ['DE', 'FR', 'US'])[1 + (gs % 3)]
FROM generate_series(1, 3) AS gs;

INSERT INTO transactions (transaction_id, customer_id, activity_type, amount, currency, status, created_at)
SELECT ('c0000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
       '11111111-1111-1111-1111-111111111111',
       'CRYPTO',
       (0.05 + gs * 0.01)::decimal(18, 2),
       'BTC',
       (ARRAY ['COMPLETED', 'PENDING'])[1 + (gs % 2)],
       TIMESTAMP '2026-02-10 00:00:00' + (gs || ' days')::interval
FROM generate_series(1, 3) AS gs;

INSERT INTO crypto_activity (transaction_id, blockchain, wallet_address_from, wallet_address_to, tx_hash,
                              exchange_name)
SELECT ('c0000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
       'BTC',
       '1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfN' || gs,
       '3J98t1WpEZ73CNmQviecrnyiWrnqRhWNL' || gs,
       'tx_hash_' || gs,
       'Kraken'
FROM generate_series(1, 3) AS gs;

-- Maria: PAYMENT activity.
INSERT INTO transactions (transaction_id, customer_id, activity_type, amount, currency, status, created_at)
SELECT ('d0000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
       '22222222-2222-2222-2222-222222222222',
       'PAYMENT',
       (1000 + gs * 75)::decimal(18, 2),
       'USD',
       (ARRAY ['COMPLETED', 'PENDING', 'REVERSED'])[1 + (gs % 3)],
       TIMESTAMP '2026-01-15 00:00:00' + (gs || ' days')::interval
FROM generate_series(1, 6) AS gs;

INSERT INTO payment_activity (transaction_id, payment_method, sender_account, receiver_account,
                               receiver_bank_country)
SELECT ('d0000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
       (ARRAY ['ACH', 'WIRE', 'SWIFT', 'P2P'])[1 + (gs % 4)],
       'US' || lpad((3000000 + gs)::text, 20, '0'),
       'GB' || lpad((4000000 + gs)::text, 20, '0'),
       (ARRAY ['GB', 'US', 'IT'])[1 + (gs % 3)]
FROM generate_series(1, 6) AS gs;

-- John: CRYPTO activity.
INSERT INTO transactions (transaction_id, customer_id, activity_type, amount, currency, status, created_at)
SELECT ('e0000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
       '33333333-3333-3333-3333-333333333333',
       'CRYPTO',
       (0.5 + gs * 0.2)::decimal(18, 2),
       'ETH',
       (ARRAY ['COMPLETED', 'PENDING', 'FAILED'])[1 + (gs % 3)],
       TIMESTAMP '2026-01-20 00:00:00' + (gs || ' days')::interval
FROM generate_series(1, 6) AS gs;

INSERT INTO crypto_activity (transaction_id, blockchain, wallet_address_from, wallet_address_to, tx_hash,
                              exchange_name)
SELECT ('e0000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
       'ETH',
       '0xAbC123def456' || gs,
       '0xDef456abc789' || gs,
       'eth_tx_hash_' || gs,
       CASE WHEN gs % 2 = 0 THEN 'Coinbase' ELSE NULL END
FROM generate_series(1, 6) AS gs;

-- Angelo: a broader spread of CARD activity across the ~14 months preceding the seed's anchor date
-- (2024-11-01 .. 2026-01-01, two transactions/month), so the analytics endpoint's WEEK/MONTH/YEAR
-- granularities have real buckets to render, not just the tight Jan-2026 cluster above. Anchored to a
-- fixed calendar date (not now()) so the seed stays deterministic; pick a `from`/`to` range covering
-- this window in the date picker to see it (Phase 3).
INSERT INTO transactions (transaction_id, customer_id, activity_type, amount, currency, status, created_at)
SELECT ('f0000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
       '11111111-1111-1111-1111-111111111111',
       'CARD',
       (25 + gs * 4.10)::decimal(18, 2),
       'EUR',
       (ARRAY ['COMPLETED', 'PENDING', 'FAILED', 'REVERSED'])[1 + (gs % 4)],
       TIMESTAMP '2024-11-01 00:00:00' + ((gs / 2) || ' months')::interval + ((gs % 2) || ' days')::interval
FROM generate_series(0, 27) AS gs;

INSERT INTO card_activity (transaction_id, card_pan, card_type, merchant_name, mcc_code, card_present,
                            authorization_code, decline_reason)
SELECT ('f0000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
       '****' || lpad((2000 + gs)::text, 4, '0'),
       (ARRAY ['DEBIT', 'CREDIT', 'PREPAID'])[1 + (gs % 3)],
       (ARRAY ['Amazon', 'Starbucks', 'Uber', 'Carrefour', 'Apple Store'])[1 + (gs % 5)],
       lpad((6000 + gs)::text, 4, '0'),
       (gs % 2 = 0),
       'AUTH-HIST' || gs,
       CASE WHEN gs % 4 = 3 THEN 'Insufficient funds' ELSE NULL END
FROM generate_series(0, 27) AS gs;

-- Phase 4: risk rules used as RAG context for the AI risk assessment (docs/development/PHASE_4_PLAN.md).
INSERT INTO risk_rules (rule_id, rule_name, applies_to, threshold_logic, weight)
VALUES ('90000000-0000-0000-0000-000000000001',
        'High-value transaction',
        'ALL',
        'Transaction amount exceeds 5,000 in its transaction currency, regardless of activity type.',
        25.00),
       ('90000000-0000-0000-0000-000000000002',
        'Repeated failed or reversed activity',
        'ALL',
        'Transaction status is FAILED or REVERSED, which may indicate a declined attempt or a walked-back transfer.',
        10.00),
       ('90000000-0000-0000-0000-000000000003',
        'Card-not-present at high-chargeback merchant category',
        'CARD',
        'Card-not-present (cardPresent = false) transaction at a merchant category code commonly associated with '
        || 'high chargeback rates (e.g. electronics, travel, digital goods).',
        20.00),
       ('90000000-0000-0000-0000-000000000004',
        'Declined authorization',
        'CARD',
        'The card transaction carries a non-null decline reason, indicating the issuer refused authorization.',
        15.00),
       ('90000000-0000-0000-0000-000000000005',
        'Cross-border payment to a non-cooperative jurisdiction',
        'PAYMENT',
        'The receiving bank country differs from the sending account''s country and is not among the commonly '
        || 'trusted corridors (e.g. EU/EEA, US, UK).',
        30.00),
       ('90000000-0000-0000-0000-000000000006',
        'High-value wire or SWIFT transfer',
        'PAYMENT',
        'Payment method is WIRE or SWIFT and the amount exceeds 10,000 in its transaction currency.',
        25.00),
       ('90000000-0000-0000-0000-000000000007',
        'Crypto transfer without a known exchange counterpart',
        'CRYPTO',
        'The crypto transaction has no associated exchange name, suggesting a direct wallet-to-wallet transfer '
        || 'outside a regulated exchange.',
        20.00),
       ('90000000-0000-0000-0000-000000000008',
        'High-value crypto transfer',
        'CRYPTO',
        'Crypto transaction amount exceeds the equivalent of 10,000 in its stated currency/ticker.',
        30.00);

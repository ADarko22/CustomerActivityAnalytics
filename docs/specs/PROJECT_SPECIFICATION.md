# Specification Document for the Customer Activity Analytics Web Application

## Goal

Enable Customer Care Operators to analyze the activity and the risk of clients.

The Web Application is a Dashboard displaying the activity of clients and monitor the required information.

## Features

1. The operator should be able to search for a Customer by Customer ID and review the Customer's activity. There are
   three types of activity: card activity, payment activity, and cryptocurrency activity. Each activity has a custom set
   of properties, specific to its typology, and a shared set of properties, common to the transactional nature.
2. The dashboard should provide a clear view of the Customer's activity, with an overview of these transactions sorted
   by date and paginated to guarantee a high user experience. Activity specific details are displayed when selecting the
   activity or hovering on it. A filter dropdown should be available to visualize only the activity of a specific type,
   or all together.
3. The dashboard should provide an insight view for aggregated statistics over custom time-ranges:
    1. The view should show aggregated info of the total count, over a time period (by specifying the range with a
       date-picker), for the filtered transactions (each common property can be used as a filter).
    2. The view should show aggregated info of the total sum of amounts by currency, over a time period (by specifying
       the range with a date-picker), for the filtered transactions (each common property can be used as a filter).
    3. When the filter dropdown selects a specific activity type (instead of all), the aggregation views mentioned
       above  (in 1 and 2) can allow more activity-specific filters.
4. The operator should be able to trigger an AI Risk Assessment for a single transaction. The assessment should consist
   of a risk level, a summary of findings, and a set of recommendations. Since the assessment may take time, the
   operator should be able to received live-updates of the assessment processing (i.e. processing user prompt,
   retrieving risk rules context, retrieving historical assessments, etc.) until the final result is available and
   displayed.
5. The AI Risk Assessment is performed on the backend, by integrating with a configurable AI Provider, using a
   configurable model, and relying on prompt engineering managed as a code in the backend, context augmentation with RAG
   based on risk rules specified in a relational database. The assessment should be persisted in the database and made
   available to the operator in the web app.
6. The Risk Level score should be computed taking into consideration the risk rule weight, the matching with the
   threshold logi, and other factors.
7. The operator with admin rights should be able to edit risk rules from the web application.
8. The operator should be able to visualize the history of all AI Risk Assessments per transaction.
9. Operators should be able to login. Each operator has its own identity and a set of access rights.
10. RAG should be used to augment the context with risk rules and other sources, such as policies and regulations.

## Data Model

### customers

Minimal entity representing a Customer.

| Column Name     | Data Type | Description                |
|-----------------|-----------|----------------------------|
| **customer_id** | UUID (PK) | Unique customer identifier |
| **first_name**  | VARCHAR   | i.e. Angelo                |
| **last_name**   | VARCHAR   | i.e. Buono                 |

### transactions

The parent entity representing the common properties of different activity types.

| Column Name        | Data Type             | Description                             |
|--------------------|-----------------------|-----------------------------------------|
| **transaction_id** | UUID (PK)             | Unique transaction identifier           |
| **customer_id**    | UUID (FK → customers) | Owner of the activity                   |
| **activity_type**  | ENUM                  | 'CARD', 'PAYMENT', 'CRYPTO'             |
| **amount**         | DECIMAL(18,2)         | Transaction amount                      |
| **currency**       | VARCHAR(10)           | ISO currency code or crypto ticker      |
| **status**         | VARCHAR               | Completed / Pending / Failed / Reversed |
| **created_at**     | TIMESTAMP             | When the transaction occurred           |

### card_activity

Extends from the **transactions** entity and specifies the details of a card transaction.

| Column Name            | Data Type                    | Description                   |
|------------------------|------------------------------|-------------------------------|
| **transaction_id**     | UUID (PK, FK → transactions) | Links to base transaction     |
| **card_pan**           | VARCHAR                      | Masked PAN (e.g. ****1234)    |
| **card_type**          | VARCHAR                      | Debit / Credit / Prepaid      |
| **merchant_name**      | VARCHAR                      | Merchant descriptor           |
| **mcc_code**           | VARCHAR(4)                   | Merchant category code        |
| **card_present**       | BOOLEAN                      | Physical vs card-not-present  |
| **authorization_code** | VARCHAR                      | Issuer auth code              |
| **decline_reason**     | VARCHAR                      | Reason if declined (nullable) |

### payment_activity

Extends from the **transactions** entity and specifies the details of a payment transaction.

| Column Name               | Data Type                    | Description               |
|---------------------------|------------------------------|---------------------------|
| **transaction_id**        | UUID (PK, FK → transactions) | Links to base transaction |
| **payment_method**        | VARCHAR                      | ACH / Wire / SWIFT / P2P  |
| **sender_account**        | VARCHAR                      | Originating account IBAN  |
| **receiver_account**      | VARCHAR                      | Destination account IBAN  |
| **receiver_bank_country** | CHAR(2)                      | Beneficiary bank country  |

### crypto_activity

Extends from the **transactions** entity and specifies the details of a crypto transaction.

| Column Name             | Data Type                    | Description               |
|-------------------------|------------------------------|---------------------------|
| **transaction_id**      | UUID (PK, FK → transactions) | Links to base transaction |
| **blockchain**          | VARCHAR                      | BTC / ETH / etc.          |
| **wallet_address_from** | VARCHAR                      | Source wallet             |
| **wallet_address_to**   | VARCHAR                      | Destination wallet        |
| **tx_hash**             | VARCHAR                      | On-chain transaction hash |
| **exchange_name**       | VARCHAR                      | exchange, if any          |

### risk_assessments

Entity representing the outcome of a risk assessment.

| Column Name            | Data Type                | Description                |
|------------------------|--------------------------|----------------------------|
| **assessment_id**      | UUID (PK)                | Unique assessment record   |
| **transaction_id**     | UUID (FK → transactions) | Transaction being scored   |
| **rule_id**            | UUID (FK → risk_rules)   | Rule that triggered        |
| **triggered_at**       | TIMESTAMP                | When the rule fired        |
| **score_contribution** | DECIMAL(5,2)             | Points added to risk_score |

### risk_rules

Entity specifying the Risk Rules to be checked for the assessment of a transaction.

| Column Name         | Data Type    | Description                            |
|---------------------|--------------|----------------------------------------|
| **rule_id**         | UUID (PK)    | Rule identifier                        |
| **rule_name**       | VARCHAR      | e.g. "High-value cross-border payment" |
| **applies_to**      | ENUM         | CARD / PAYMENT / CRYPTO / ALL          |
| **threshold_logic** | TEXT         | Rule condition                         |
| **weight**          | DECIMAL(5,2) | Default score weight                   |
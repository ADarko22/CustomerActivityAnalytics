package io.github.adarko22.customeractivityanalytics.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The base {@link Transaction} columns, bundled so activity-type subclass constructors stay short.
 */
public record TransactionCoreFields(
    UUID transactionId,
    UUID customerId,
    BigDecimal amount,
    String currency,
    TransactionStatus status,
    Instant createdAt) {}

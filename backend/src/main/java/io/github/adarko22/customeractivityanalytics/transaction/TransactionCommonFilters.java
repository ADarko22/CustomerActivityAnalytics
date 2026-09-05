package io.github.adarko22.customeractivityanalytics.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The transaction filter fields shared by every activity type, bundled to keep call sites short.
 */
public record TransactionCommonFilters(
    TransactionStatus status,
    Instant from,
    Instant to,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    String currency) {}

package io.github.adarko22.customeractivityanalytics.transaction.dto;

import io.github.adarko22.customeractivityanalytics.transaction.ActivityType;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CryptoTransactionDto(
    UUID transactionId,
    UUID customerId,
    ActivityType activityType,
    BigDecimal amount,
    String currency,
    TransactionStatus status,
    Instant createdAt,
    String blockchain,
    String walletAddressFrom,
    String walletAddressTo,
    String txHash,
    String exchangeName)
    implements TransactionDto {}

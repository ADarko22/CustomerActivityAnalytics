package io.github.adarko22.customeractivityanalytics.transaction;

/** Optional per-activity-type filter values, shared by the overview and analytics endpoints. */
public record TransactionTypeFilters(
    String cardType,
    String merchantName,
    String mccCode,
    Boolean cardPresent,
    String paymentMethod,
    String senderAccount,
    String receiverAccount,
    String receiverBankCountry,
    String blockchain,
    String walletAddressFrom,
    String walletAddressTo,
    String exchangeName) {}

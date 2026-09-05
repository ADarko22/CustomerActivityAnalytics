package io.github.adarko22.customeractivityanalytics.transaction.card;

/** The card-specific columns of a {@link CardActivity}, bundled to keep its constructor short. */
public record CardActivityDetails(
    String cardPan,
    String cardType,
    String merchantName,
    String mccCode,
    boolean cardPresent,
    String authorizationCode,
    String declineReason) {}

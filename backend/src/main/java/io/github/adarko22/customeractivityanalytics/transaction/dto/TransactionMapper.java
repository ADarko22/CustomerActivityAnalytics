package io.github.adarko22.customeractivityanalytics.transaction.dto;

import io.github.adarko22.customeractivityanalytics.transaction.ActivityType;
import io.github.adarko22.customeractivityanalytics.transaction.Transaction;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivity;
import io.github.adarko22.customeractivityanalytics.transaction.crypto.CryptoActivity;
import io.github.adarko22.customeractivityanalytics.transaction.payment.PaymentActivity;

/** Maps a polymorphic {@link Transaction} subtype to its matching sealed {@link TransactionDto}. */
public final class TransactionMapper {

  private TransactionMapper() {}

  public static TransactionDto toDto(Transaction transaction) {
    return switch (transaction) {
      case CardActivity card ->
          new CardTransactionDto(
              card.getTransactionId(),
              card.getCustomerId(),
              ActivityType.CARD,
              card.getAmount(),
              card.getCurrency(),
              card.getStatus(),
              card.getCreatedAt(),
              card.getCardPan(),
              card.getCardType(),
              card.getMerchantName(),
              card.getMccCode(),
              card.isCardPresent(),
              card.getAuthorizationCode(),
              card.getDeclineReason());
      case PaymentActivity payment ->
          new PaymentTransactionDto(
              payment.getTransactionId(),
              payment.getCustomerId(),
              ActivityType.PAYMENT,
              payment.getAmount(),
              payment.getCurrency(),
              payment.getStatus(),
              payment.getCreatedAt(),
              payment.getPaymentMethod(),
              payment.getSenderAccount(),
              payment.getReceiverAccount(),
              payment.getReceiverBankCountry());
      case CryptoActivity crypto ->
          new CryptoTransactionDto(
              crypto.getTransactionId(),
              crypto.getCustomerId(),
              ActivityType.CRYPTO,
              crypto.getAmount(),
              crypto.getCurrency(),
              crypto.getStatus(),
              crypto.getCreatedAt(),
              crypto.getBlockchain(),
              crypto.getWalletAddressFrom(),
              crypto.getWalletAddressTo(),
              crypto.getTxHash(),
              crypto.getExchangeName());
      default ->
          throw new IllegalStateException("Unknown transaction type: " + transaction.getClass());
    };
  }
}

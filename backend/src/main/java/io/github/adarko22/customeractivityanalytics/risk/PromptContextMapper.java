package io.github.adarko22.customeractivityanalytics.risk;

import io.github.adarko22.customeractivityanalytics.transaction.dto.CardTransactionDto;
import io.github.adarko22.customeractivityanalytics.transaction.dto.CryptoTransactionDto;
import io.github.adarko22.customeractivityanalytics.transaction.dto.PaymentTransactionDto;
import io.github.adarko22.customeractivityanalytics.transaction.dto.TransactionDto;
import org.springframework.stereotype.Component;

/**
 * Renders a transaction into the PII-scrubbed context injected into the AI risk-assessment user
 * prompt. This is the single reviewable allow-list of fields the model ever sees (docs/development/
 * PHASE_4_PLAN.md Clarification #3) — {@code customerId}, card PAN/authorization code, payment
 * account numbers, and crypto wallet addresses/tx hashes are never included, either because they're
 * PII (customer identity, account numbers) or attacker-usable/traceable detail (wallet addresses,
 * tx hashes), per the phase's Security & Data Protection NFR.
 */
@Component
public class PromptContextMapper {

  public String map(TransactionDto transaction) {
    StringBuilder sb = new StringBuilder();
    sb.append("transactionId: ").append(transaction.transactionId()).append('\n');
    sb.append("activityType: ").append(transaction.activityType()).append('\n');
    sb.append("amount: ").append(transaction.amount()).append('\n');
    sb.append("currency: ").append(transaction.currency()).append('\n');
    sb.append("status: ").append(transaction.status()).append('\n');
    sb.append("createdAt: ").append(transaction.createdAt()).append('\n');
    switch (transaction) {
      case CardTransactionDto card -> appendCard(sb, card);
      case PaymentTransactionDto payment -> appendPayment(sb, payment);
      case CryptoTransactionDto crypto -> appendCrypto(sb, crypto);
    }
    return sb.toString();
  }

  private static void appendCard(StringBuilder sb, CardTransactionDto card) {
    sb.append("cardType: ").append(card.cardType()).append('\n');
    sb.append("merchantName: ").append(card.merchantName()).append('\n');
    sb.append("mccCode: ").append(card.mccCode()).append('\n');
    sb.append("cardPresent: ").append(card.cardPresent()).append('\n');
    sb.append("declineReason: ").append(card.declineReason()).append('\n');
  }

  private static void appendPayment(StringBuilder sb, PaymentTransactionDto payment) {
    sb.append("paymentMethod: ").append(payment.paymentMethod()).append('\n');
    sb.append("receiverBankCountry: ").append(payment.receiverBankCountry()).append('\n');
  }

  private static void appendCrypto(StringBuilder sb, CryptoTransactionDto crypto) {
    sb.append("blockchain: ").append(crypto.blockchain()).append('\n');
    sb.append("exchangeName: ").append(crypto.exchangeName()).append('\n');
  }
}

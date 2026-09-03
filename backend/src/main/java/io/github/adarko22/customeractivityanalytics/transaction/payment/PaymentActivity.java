package io.github.adarko22.customeractivityanalytics.transaction.payment;

import io.github.adarko22.customeractivityanalytics.transaction.Transaction;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_activity")
@PrimaryKeyJoinColumn(name = "transaction_id")
@DiscriminatorValue("PAYMENT")
public class PaymentActivity extends Transaction {

  @Column(name = "payment_method", nullable = false)
  private String paymentMethod;

  @Column(name = "sender_account", nullable = false)
  private String senderAccount;

  @Column(name = "receiver_account", nullable = false)
  private String receiverAccount;

  @Column(name = "receiver_bank_country", nullable = false)
  private String receiverBankCountry;

  protected PaymentActivity() {}

  public PaymentActivity(
      UUID transactionId,
      UUID customerId,
      BigDecimal amount,
      String currency,
      TransactionStatus status,
      Instant createdAt,
      String paymentMethod,
      String senderAccount,
      String receiverAccount,
      String receiverBankCountry) {
    super(transactionId, customerId, amount, currency, status, createdAt);
    this.paymentMethod = paymentMethod;
    this.senderAccount = senderAccount;
    this.receiverAccount = receiverAccount;
    this.receiverBankCountry = receiverBankCountry;
  }

  public String getPaymentMethod() {
    return paymentMethod;
  }

  public String getSenderAccount() {
    return senderAccount;
  }

  public String getReceiverAccount() {
    return receiverAccount;
  }

  public String getReceiverBankCountry() {
    return receiverBankCountry;
  }
}

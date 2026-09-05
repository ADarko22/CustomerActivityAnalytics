package io.github.adarko22.customeractivityanalytics.transaction.payment;

import io.github.adarko22.customeractivityanalytics.transaction.Transaction;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionCoreFields;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

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
      TransactionCoreFields core,
      String paymentMethod,
      String senderAccount,
      String receiverAccount,
      String receiverBankCountry) {
    super(
        core.transactionId(),
        core.customerId(),
        core.amount(),
        core.currency(),
        core.status(),
        core.createdAt());
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

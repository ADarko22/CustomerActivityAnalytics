package io.github.adarko22.customeractivityanalytics.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "activity_type")
public abstract class Transaction {

  @Id
  @Column(name = "transaction_id")
  private UUID transactionId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "amount", nullable = false)
  private BigDecimal amount;

  @Column(name = "currency", nullable = false)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private TransactionStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Transaction() {}

  protected Transaction(
      UUID transactionId,
      UUID customerId,
      BigDecimal amount,
      String currency,
      TransactionStatus status,
      Instant createdAt) {
    this.transactionId = transactionId;
    this.customerId = customerId;
    this.amount = amount;
    this.currency = currency;
    this.status = status;
    this.createdAt = createdAt;
  }

  public UUID getTransactionId() {
    return transactionId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public TransactionStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

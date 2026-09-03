package io.github.adarko22.customeractivityanalytics.transaction.crypto;

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
@Table(name = "crypto_activity")
@PrimaryKeyJoinColumn(name = "transaction_id")
@DiscriminatorValue("CRYPTO")
public class CryptoActivity extends Transaction {

  @Column(name = "blockchain", nullable = false)
  private String blockchain;

  @Column(name = "wallet_address_from", nullable = false)
  private String walletAddressFrom;

  @Column(name = "wallet_address_to", nullable = false)
  private String walletAddressTo;

  @Column(name = "tx_hash", nullable = false)
  private String txHash;

  @Column(name = "exchange_name")
  private String exchangeName;

  protected CryptoActivity() {}

  public CryptoActivity(
      UUID transactionId,
      UUID customerId,
      BigDecimal amount,
      String currency,
      TransactionStatus status,
      Instant createdAt,
      String blockchain,
      String walletAddressFrom,
      String walletAddressTo,
      String txHash,
      String exchangeName) {
    super(transactionId, customerId, amount, currency, status, createdAt);
    this.blockchain = blockchain;
    this.walletAddressFrom = walletAddressFrom;
    this.walletAddressTo = walletAddressTo;
    this.txHash = txHash;
    this.exchangeName = exchangeName;
  }

  public String getBlockchain() {
    return blockchain;
  }

  public String getWalletAddressFrom() {
    return walletAddressFrom;
  }

  public String getWalletAddressTo() {
    return walletAddressTo;
  }

  public String getTxHash() {
    return txHash;
  }

  public String getExchangeName() {
    return exchangeName;
  }
}

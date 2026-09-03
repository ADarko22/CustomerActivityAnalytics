package io.github.adarko22.customeractivityanalytics.transaction.card;

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
@Table(name = "card_activity")
@PrimaryKeyJoinColumn(name = "transaction_id")
@DiscriminatorValue("CARD")
public class CardActivity extends Transaction {

  @Column(name = "card_pan", nullable = false)
  private String cardPan;

  @Column(name = "card_type", nullable = false)
  private String cardType;

  @Column(name = "merchant_name", nullable = false)
  private String merchantName;

  @Column(name = "mcc_code", nullable = false)
  private String mccCode;

  @Column(name = "card_present", nullable = false)
  private boolean cardPresent;

  @Column(name = "authorization_code")
  private String authorizationCode;

  @Column(name = "decline_reason")
  private String declineReason;

  protected CardActivity() {}

  public CardActivity(
      UUID transactionId,
      UUID customerId,
      BigDecimal amount,
      String currency,
      TransactionStatus status,
      Instant createdAt,
      String cardPan,
      String cardType,
      String merchantName,
      String mccCode,
      boolean cardPresent,
      String authorizationCode,
      String declineReason) {
    super(transactionId, customerId, amount, currency, status, createdAt);
    this.cardPan = cardPan;
    this.cardType = cardType;
    this.merchantName = merchantName;
    this.mccCode = mccCode;
    this.cardPresent = cardPresent;
    this.authorizationCode = authorizationCode;
    this.declineReason = declineReason;
  }

  public String getCardPan() {
    return cardPan;
  }

  public String getCardType() {
    return cardType;
  }

  public String getMerchantName() {
    return merchantName;
  }

  public String getMccCode() {
    return mccCode;
  }

  public boolean isCardPresent() {
    return cardPresent;
  }

  public String getAuthorizationCode() {
    return authorizationCode;
  }

  public String getDeclineReason() {
    return declineReason;
  }
}

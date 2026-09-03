package io.github.adarko22.customeractivityanalytics.transaction.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.github.adarko22.customeractivityanalytics.transaction.ActivityType;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "activityType",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = CardTransactionDto.class, name = "CARD"),
  @JsonSubTypes.Type(value = PaymentTransactionDto.class, name = "PAYMENT"),
  @JsonSubTypes.Type(value = CryptoTransactionDto.class, name = "CRYPTO"),
})
public sealed interface TransactionDto
    permits CardTransactionDto, PaymentTransactionDto, CryptoTransactionDto {

  UUID transactionId();

  UUID customerId();

  ActivityType activityType();

  BigDecimal amount();

  String currency();

  TransactionStatus status();

  Instant createdAt();
}

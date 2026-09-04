package io.github.adarko22.customeractivityanalytics.risk.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adarko22.customeractivityanalytics.transaction.ActivityType;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import io.github.adarko22.customeractivityanalytics.transaction.dto.CardTransactionDto;
import io.github.adarko22.customeractivityanalytics.transaction.dto.CryptoTransactionDto;
import io.github.adarko22.customeractivityanalytics.transaction.dto.PaymentTransactionDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromptContextMapperTest {

  private final PromptContextMapper mapper = new PromptContextMapper();
  private final UUID customerId = UUID.randomUUID();
  private final UUID transactionId = UUID.randomUUID();

  @Test
  void cardContextOmitsPiiAndIncludesCategoricalSignals() {
    CardTransactionDto card =
        new CardTransactionDto(
            transactionId,
            customerId,
            ActivityType.CARD,
            new BigDecimal("10.00"),
            "EUR",
            TransactionStatus.COMPLETED,
            Instant.now(),
            "****1234",
            "DEBIT",
            "Amazon",
            "5732",
            true,
            "AUTH-SECRET-1",
            "Insufficient funds");

    String context = mapper.map(card);

    assertThat(context).doesNotContain(customerId.toString());
    assertThat(context).doesNotContain("****1234");
    assertThat(context).doesNotContain("AUTH-SECRET-1");
    assertThat(context).contains(transactionId.toString());
    assertThat(context).contains("DEBIT");
    assertThat(context).contains("Amazon");
    assertThat(context).contains("5732");
    assertThat(context).contains("Insufficient funds");
  }

  @Test
  void paymentContextOmitsPiiAndIncludesCategoricalSignals() {
    PaymentTransactionDto payment =
        new PaymentTransactionDto(
            transactionId,
            customerId,
            ActivityType.PAYMENT,
            new BigDecimal("500.00"),
            "USD",
            TransactionStatus.COMPLETED,
            Instant.now(),
            "WIRE",
            "IT00SENDERACCOUNTNUMBER",
            "DE00RECEIVERACCOUNTNUMBER",
            "US");

    String context = mapper.map(payment);

    assertThat(context).doesNotContain(customerId.toString());
    assertThat(context).doesNotContain("IT00SENDERACCOUNTNUMBER");
    assertThat(context).doesNotContain("DE00RECEIVERACCOUNTNUMBER");
    assertThat(context).contains(transactionId.toString());
    assertThat(context).contains("WIRE");
    assertThat(context).contains("US");
  }

  @Test
  void cryptoContextOmitsPiiAndIncludesCategoricalSignals() {
    CryptoTransactionDto crypto =
        new CryptoTransactionDto(
            transactionId,
            customerId,
            ActivityType.CRYPTO,
            new BigDecimal("0.50"),
            "BTC",
            TransactionStatus.COMPLETED,
            Instant.now(),
            "BTC",
            "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfN",
            "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNL",
            "tx-hash-secret",
            "Kraken");

    String context = mapper.map(crypto);

    assertThat(context).doesNotContain(customerId.toString());
    assertThat(context).doesNotContain("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfN");
    assertThat(context).doesNotContain("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNL");
    assertThat(context).doesNotContain("tx-hash-secret");
    assertThat(context).contains(transactionId.toString());
    assertThat(context).contains("BTC");
    assertThat(context).contains("Kraken");
  }
}

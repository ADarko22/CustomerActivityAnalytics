package io.github.adarko22.customeractivityanalytics.risk.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PiiGuardrailServiceTest {

  private final PiiGuardrailService guardrail =
      new PiiGuardrailService(
          new PiiGuardrailProperties(
              List.of(
                  new PiiGuardrailProperties.PatternRule("CARD_PAN", "\\b(?:\\d[ -]?){13,19}\\b"),
                  new PiiGuardrailProperties.PatternRule(
                      "IBAN", "\\b[A-Z]{2}\\d{2}[A-Z0-9]{10,30}\\b"),
                  new PiiGuardrailProperties.PatternRule(
                      "EMAIL", "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"),
                  new PiiGuardrailProperties.PatternRule(
                      "CRYPTO_WALLET",
                      "\\b0x[a-fA-F0-9]{40}\\b|\\b(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,39}\\b"))));

  @Test
  void detectsCardPan() {
    Optional<String> violation = guardrail.scan("card number 4111111111111111 was used");

    assertThat(violation).contains("CARD_PAN");
  }

  @Test
  void detectsIban() {
    Optional<String> violation = guardrail.scan("beneficiary IBAN DE89370400440532013000 noted");

    assertThat(violation).contains("IBAN");
  }

  @Test
  void detectsEmail() {
    Optional<String> violation = guardrail.scan("contact customer at jane.doe@example.com please");

    assertThat(violation).contains("EMAIL");
  }

  @Test
  void detectsCryptoWallet() {
    Optional<String> violation =
        guardrail.scan("funds sent to 1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfN wallet");

    assertThat(violation).contains("CRYPTO_WALLET");
  }

  @Test
  void cleanPromptProducesNoViolation() {
    Optional<String> violation =
        guardrail.scan(
            "transactionId=abc123, amount=6000.00, mcc=5732, cardPresent=false, rule weight 25.00");

    assertThat(violation).isEmpty();
  }
}

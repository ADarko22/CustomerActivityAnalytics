package io.github.adarko22.customeractivityanalytics.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adarko22.customeractivityanalytics.AbstractPostgresIntegrationTest;
import io.github.adarko22.customeractivityanalytics.customer.Customer;
import io.github.adarko22.customeractivityanalytics.customer.CustomerRepository;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivity;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivityRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionRepositoryTest extends AbstractPostgresIntegrationTest {

  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private CardActivityRepository cardActivityRepository;

  private UUID customerId;

  @BeforeEach
  void setUp() {
    customerId = UUID.randomUUID();
    customerRepository.save(new Customer(customerId, "Angelo", "Buono"));
  }

  @Test
  void filtersAndSortsByCommonFieldsAcrossTypes() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    saveCard(
        new BigDecimal("10.00"), "EUR", TransactionStatus.COMPLETED, now.minus(2, ChronoUnit.DAYS));
    saveCard(
        new BigDecimal("20.00"), "EUR", TransactionStatus.PENDING, now.minus(1, ChronoUnit.DAYS));
    saveCard(new BigDecimal("30.00"), "USD", TransactionStatus.COMPLETED, now);

    Page<Transaction> page =
        transactionRepository.findAll(
            TransactionSpecifications.<Transaction>common(
                customerId, TransactionStatus.COMPLETED, null, null, null, null, null),
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

    assertThat(page.getContent()).hasSize(2);
    assertThat(page.getContent().get(0).getAmount()).isEqualByComparingTo("30.00");
  }

  @Test
  void filtersByAmountRangeAndCurrency() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    saveCard(new BigDecimal("10.00"), "EUR", TransactionStatus.COMPLETED, now);
    saveCard(new BigDecimal("50.00"), "EUR", TransactionStatus.COMPLETED, now);
    saveCard(new BigDecimal("50.00"), "USD", TransactionStatus.COMPLETED, now);

    Page<Transaction> page =
        transactionRepository.findAll(
            TransactionSpecifications.<Transaction>common(
                customerId,
                null,
                null,
                null,
                new BigDecimal("20.00"),
                new BigDecimal("100.00"),
                "EUR"),
            PageRequest.of(0, 10));

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).getAmount()).isEqualByComparingTo("50.00");
  }

  private void saveCard(
      BigDecimal amount, String currency, TransactionStatus status, Instant createdAt) {
    cardActivityRepository.save(
        new CardActivity(
            UUID.randomUUID(),
            customerId,
            amount,
            currency,
            status,
            createdAt,
            "****1234",
            "DEBIT",
            "Amazon",
            "5732",
            true,
            "AUTH1",
            null));
  }
}

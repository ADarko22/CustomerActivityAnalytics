package io.github.adarko22.customeractivityanalytics.transaction.card;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adarko22.customeractivityanalytics.AbstractPostgresIntegrationTest;
import io.github.adarko22.customeractivityanalytics.customer.Customer;
import io.github.adarko22.customeractivityanalytics.customer.CustomerRepository;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
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
class CardActivityRepositoryTest extends AbstractPostgresIntegrationTest {

  @Autowired private CustomerRepository customerRepository;
  @Autowired private CardActivityRepository cardActivityRepository;

  private UUID customerId;

  @BeforeEach
  void setUp() {
    customerId = UUID.randomUUID();
    customerRepository.save(new Customer(customerId, "Angelo", "Buono"));
  }

  @Test
  void filtersByCardTypeAndSortsByMerchantName() {
    cardActivityRepository.save(card("DEBIT", "Zebra Shop"));
    cardActivityRepository.save(card("CREDIT", "Amazon"));
    cardActivityRepository.save(card("DEBIT", "Apple Store"));

    Page<CardActivity> page =
        cardActivityRepository.findAll(
            CardActivitySpecifications.filter(
                customerId, null, null, null, null, null, null, "DEBIT", null, null, null),
            PageRequest.of(0, 10, Sort.by("merchantName")));

    assertThat(page.getContent())
        .extracting(CardActivity::getMerchantName)
        .containsExactly("Apple Store", "Zebra Shop");
  }

  private CardActivity card(String cardType, String merchantName) {
    return new CardActivity(
        UUID.randomUUID(),
        customerId,
        new BigDecimal("25.00"),
        "EUR",
        TransactionStatus.COMPLETED,
        Instant.now(),
        "****1234",
        cardType,
        merchantName,
        "5732",
        true,
        "AUTH1",
        null);
  }
}

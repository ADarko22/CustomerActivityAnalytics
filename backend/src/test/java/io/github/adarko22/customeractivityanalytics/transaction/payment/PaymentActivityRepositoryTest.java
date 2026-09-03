package io.github.adarko22.customeractivityanalytics.transaction.payment;

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
class PaymentActivityRepositoryTest extends AbstractPostgresIntegrationTest {

  @Autowired private CustomerRepository customerRepository;
  @Autowired private PaymentActivityRepository paymentActivityRepository;

  private UUID customerId;

  @BeforeEach
  void setUp() {
    customerId = UUID.randomUUID();
    customerRepository.save(new Customer(customerId, "Maria", "Rossi"));
  }

  @Test
  void filtersByPaymentMethodAndSortsBySenderAccount() {
    paymentActivityRepository.save(payment("WIRE", "CCC"));
    paymentActivityRepository.save(payment("ACH", "BBB"));
    paymentActivityRepository.save(payment("WIRE", "AAA"));

    Page<PaymentActivity> page =
        paymentActivityRepository.findAll(
            PaymentActivitySpecifications.filter(
                customerId, null, null, null, null, null, null, "WIRE", null, null, null),
            PageRequest.of(0, 10, Sort.by("senderAccount")));

    assertThat(page.getContent())
        .extracting(PaymentActivity::getSenderAccount)
        .containsExactly("AAA", "CCC");
  }

  private PaymentActivity payment(String paymentMethod, String senderAccount) {
    return new PaymentActivity(
        UUID.randomUUID(),
        customerId,
        new BigDecimal("500.00"),
        "USD",
        TransactionStatus.COMPLETED,
        Instant.now(),
        paymentMethod,
        senderAccount,
        "RECV123",
        "US");
  }
}

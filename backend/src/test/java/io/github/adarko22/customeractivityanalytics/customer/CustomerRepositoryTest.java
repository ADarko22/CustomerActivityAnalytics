package io.github.adarko22.customeractivityanalytics.customer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adarko22.customeractivityanalytics.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CustomerRepositoryTest extends AbstractPostgresIntegrationTest {

  @Autowired private CustomerRepository customerRepository;

  @Test
  void blankQueryReturnsAllCustomersInRequestedOrder() {
    customerRepository.save(new Customer(UUID.randomUUID(), "Zoe", "Adams"));
    customerRepository.save(new Customer(UUID.randomUUID(), "Angelo", "Buono"));

    Page<Customer> page =
        customerRepository.search("", PageRequest.of(0, 10, Sort.by("lastName", "firstName")));

    assertThat(page.getContent())
        .extracting(Customer::getLastName)
        .containsExactly("Adams", "Buono");
  }

  @Test
  void matchesByFirstOrLastNameCaseInsensitive() {
    customerRepository.save(new Customer(UUID.randomUUID(), "Angelo", "Buono"));
    customerRepository.save(new Customer(UUID.randomUUID(), "Maria", "Rossi"));

    Page<Customer> page = customerRepository.search("ang", PageRequest.of(0, 10));

    assertThat(page.getContent()).extracting(Customer::getFirstName).containsExactly("Angelo");
  }

  @Test
  void matchesByCustomerIdPrefix() {
    UUID customerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    customerRepository.save(new Customer(customerId, "Angelo", "Buono"));
    customerRepository.save(new Customer(UUID.randomUUID(), "Maria", "Rossi"));

    Page<Customer> page = customerRepository.search("11111111", PageRequest.of(0, 10));

    assertThat(page.getContent()).extracting(Customer::getCustomerId).containsExactly(customerId);
  }

  @Test
  void defaultPageSizeCapsResultsAtFive() {
    for (int i = 0; i < 6; i++) {
      customerRepository.save(new Customer(UUID.randomUUID(), "First" + i, "Last" + i));
    }

    Page<Customer> page = customerRepository.search("", PageRequest.of(0, 5));

    assertThat(page.getContent()).hasSize(5);
    assertThat(page.getTotalElements()).isEqualTo(6);
  }
}

package io.github.adarko22.customeractivityanalytics.transaction.crypto;

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
class CryptoActivityRepositoryTest extends AbstractPostgresIntegrationTest {

  @Autowired private CustomerRepository customerRepository;
  @Autowired private CryptoActivityRepository cryptoActivityRepository;

  private UUID customerId;

  @BeforeEach
  void setUp() {
    customerId = UUID.randomUUID();
    customerRepository.save(new Customer(customerId, "John", "Smith"));
  }

  @Test
  void filtersByBlockchainAndSortsByWalletAddressFrom() {
    cryptoActivityRepository.save(crypto("BTC", "wallet-c"));
    cryptoActivityRepository.save(crypto("ETH", "wallet-b"));
    cryptoActivityRepository.save(crypto("BTC", "wallet-a"));

    Page<CryptoActivity> page =
        cryptoActivityRepository.findAll(
            CryptoActivitySpecifications.filter(
                customerId, null, null, null, null, null, null, "BTC", null, null, null),
            PageRequest.of(0, 10, Sort.by("walletAddressFrom")));

    assertThat(page.getContent())
        .extracting(CryptoActivity::getWalletAddressFrom)
        .containsExactly("wallet-a", "wallet-c");
  }

  private CryptoActivity crypto(String blockchain, String walletAddressFrom) {
    return new CryptoActivity(
        UUID.randomUUID(),
        customerId,
        new BigDecimal("0.5"),
        "BTC",
        TransactionStatus.COMPLETED,
        Instant.now(),
        blockchain,
        walletAddressFrom,
        "wallet-to",
        "tx-hash",
        "Kraken");
  }
}

package io.github.adarko22.customeractivityanalytics.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adarko22.customeractivityanalytics.AbstractPostgresIntegrationTest;
import io.github.adarko22.customeractivityanalytics.analytics.dto.AnalyticsBucketDto;
import io.github.adarko22.customeractivityanalytics.analytics.dto.AnalyticsTimeSeriesDto;
import io.github.adarko22.customeractivityanalytics.customer.Customer;
import io.github.adarko22.customeractivityanalytics.customer.CustomerRepository;
import io.github.adarko22.customeractivityanalytics.customer.CustomerService;
import io.github.adarko22.customeractivityanalytics.transaction.ActivityType;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionRepository;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionTypeFilters;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivity;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivityRepository;
import io.github.adarko22.customeractivityanalytics.transaction.crypto.CryptoActivityRepository;
import io.github.adarko22.customeractivityanalytics.transaction.payment.PaymentActivityRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AnalyticsServiceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private CardActivityRepository cardActivityRepository;
  @Autowired private PaymentActivityRepository paymentActivityRepository;
  @Autowired private CryptoActivityRepository cryptoActivityRepository;

  private final TransactionTypeFilters noFilters =
      new TransactionTypeFilters(
          null, null, null, null, null, null, null, null, null, null, null, null);

  private AnalyticsService analyticsService;
  private UUID customerId;

  @BeforeEach
  void setUp() {
    customerId = UUID.randomUUID();
    customerRepository.save(new Customer(customerId, "Angelo", "Buono"));
    analyticsService =
        new AnalyticsService(
            new CustomerService(customerRepository),
            transactionRepository,
            cardActivityRepository,
            paymentActivityRepository,
            cryptoActivityRepository);
  }

  @Test
  void aggregatesCountsAcrossDayBucketsWithGapFilling() {
    Instant day1 = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(4, ChronoUnit.DAYS);
    Instant day3 = day1.plus(2, ChronoUnit.DAYS);
    saveCard(new BigDecimal("10.00"), "EUR", day1);
    saveCard(new BigDecimal("20.00"), "EUR", day1);
    saveCard(new BigDecimal("30.00"), "EUR", day3);

    AnalyticsTimeSeriesDto series =
        analyticsService.findTimeSeries(
            customerId, null, null, day1, day3, null, null, null, noFilters, Granularity.DAY);

    assertThat(series.buckets()).hasSize(3);
    assertThat(series.buckets().get(0).transactionCount()).isEqualTo(2);
    assertThat(series.buckets().get(1).transactionCount()).isZero();
    assertThat(series.buckets().get(2).transactionCount()).isEqualTo(1);
  }

  @Test
  void keepsMultiCurrencySumsSeparatePerBucket() {
    Instant day = Instant.now().truncatedTo(ChronoUnit.DAYS);
    saveCard(new BigDecimal("100.00"), "EUR", day);
    saveCard(new BigDecimal("50.00"), "USD", day);

    AnalyticsTimeSeriesDto series =
        analyticsService.findTimeSeries(
            customerId,
            null,
            null,
            day.minus(1, ChronoUnit.DAYS),
            day,
            null,
            null,
            null,
            noFilters,
            Granularity.DAY);

    AnalyticsBucketDto bucket =
        series.buckets().stream().filter(b -> b.transactionCount() > 0).findFirst().orElseThrow();
    assertThat(bucket.amountByCurrency())
        .isEqualTo(Map.of("EUR", new BigDecimal("100.00"), "USD", new BigDecimal("50.00")));
  }

  @Test
  void typeSpecificFilterNarrowsAggregationToMatchingRows() {
    Instant day = Instant.now().truncatedTo(ChronoUnit.DAYS);
    cardActivityRepository.save(
        new CardActivity(
            UUID.randomUUID(),
            customerId,
            new BigDecimal("10.00"),
            "EUR",
            TransactionStatus.COMPLETED,
            day,
            "****1234",
            "DEBIT",
            "Amazon",
            "5732",
            true,
            "AUTH1",
            null));
    cardActivityRepository.save(
        new CardActivity(
            UUID.randomUUID(),
            customerId,
            new BigDecimal("20.00"),
            "EUR",
            TransactionStatus.COMPLETED,
            day,
            "****5678",
            "CREDIT",
            "Starbucks",
            "5812",
            true,
            "AUTH2",
            null));

    TransactionTypeFilters debitOnly =
        new TransactionTypeFilters(
            "DEBIT", null, null, null, null, null, null, null, null, null, null, null);
    AnalyticsTimeSeriesDto series =
        analyticsService.findTimeSeries(
            customerId,
            ActivityType.CARD,
            null,
            day.minus(1, ChronoUnit.DAYS),
            day,
            null,
            null,
            null,
            debitOnly,
            Granularity.DAY);

    long totalCount =
        series.buckets().stream().mapToLong(AnalyticsBucketDto::transactionCount).sum();
    assertThat(totalCount).isEqualTo(1);
  }

  private void saveCard(BigDecimal amount, String currency, Instant createdAt) {
    cardActivityRepository.save(
        new CardActivity(
            UUID.randomUUID(),
            customerId,
            amount,
            currency,
            TransactionStatus.COMPLETED,
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

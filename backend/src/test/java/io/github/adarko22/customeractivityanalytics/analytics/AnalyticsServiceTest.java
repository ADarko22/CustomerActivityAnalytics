package io.github.adarko22.customeractivityanalytics.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.github.adarko22.customeractivityanalytics.analytics.dto.AnalyticsBucketDto;
import io.github.adarko22.customeractivityanalytics.analytics.dto.AnalyticsTimeSeriesDto;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

  @Mock private CustomerService customerService;
  @Mock private TransactionRepository transactionRepository;
  @Mock private CardActivityRepository cardActivityRepository;
  @Mock private PaymentActivityRepository paymentActivityRepository;
  @Mock private CryptoActivityRepository cryptoActivityRepository;

  private AnalyticsService analyticsService;

  private final UUID customerId = UUID.randomUUID();

  private final TransactionTypeFilters noFilters =
      new TransactionTypeFilters(
          null, null, null, null, null, null, null, null, null, null, null, null);

  @BeforeEach
  void setUp() {
    analyticsService =
        new AnalyticsService(
            customerService,
            transactionRepository,
            cardActivityRepository,
            paymentActivityRepository,
            cryptoActivityRepository);
  }

  @Test
  void dispatchesToCardRepositoryWhenActivityTypeIsCard() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
    CardActivity card = card(new BigDecimal("10.00"), "EUR", now);
    when(cardActivityRepository.findAll(any(Specification.class))).thenReturn(List.of(card));

    AnalyticsTimeSeriesDto series =
        analyticsService.findTimeSeries(
            customerId,
            ActivityType.CARD,
            null,
            now.minus(1, ChronoUnit.DAYS),
            now,
            null,
            null,
            null,
            noFilters,
            Granularity.DAY);

    assertThat(series.activityType()).isEqualTo(ActivityType.CARD);
    long totalCount =
        series.buckets().stream().mapToLong(AnalyticsBucketDto::transactionCount).sum();
    assertThat(totalCount).isEqualTo(1);
  }

  @Test
  void rejectsRangeTooShortForGranularity() {
    Instant now = Instant.now();

    assertThatThrownBy(
            () ->
                analyticsService.findTimeSeries(
                    customerId, null, null, now, now, null, null, null, noFilters, Granularity.DAY))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void propagates404WhenCustomerMissing() {
    doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
        .when(customerService)
        .requireExists(customerId);

    assertThatThrownBy(
            () ->
                analyticsService.findTimeSeries(
                    customerId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    noFilters,
                    Granularity.DAY))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void defaultsToOneMonthByDayWhenRangeOmitted() {
    when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());

    AnalyticsTimeSeriesDto series =
        analyticsService.findTimeSeries(
            customerId, null, null, null, null, null, null, null, noFilters, Granularity.DAY);

    assertThat(series.from()).isNotNull();
    assertThat(series.to()).isNotNull();
    assertThat(series.buckets()).isNotEmpty();
  }

  @Test
  void zeroFillsBucketsWithNoTransactions() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
    when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());

    AnalyticsTimeSeriesDto series =
        analyticsService.findTimeSeries(
            customerId,
            null,
            null,
            now.minus(5, ChronoUnit.DAYS),
            now,
            null,
            null,
            null,
            noFilters,
            Granularity.DAY);

    assertThat(series.buckets()).hasSize(6);
    assertThat(series.buckets())
        .allSatisfy(bucket -> assertThat(bucket.transactionCount()).isZero());
  }

  @Test
  void aggregatesCountAndAmountByCurrencyPerBucket() {
    Instant day = Instant.now().truncatedTo(ChronoUnit.DAYS);
    when(transactionRepository.findAll(any(Specification.class)))
        .thenReturn(
            List.of(
                card(new BigDecimal("10.00"), "EUR", day),
                card(new BigDecimal("5.00"), "EUR", day),
                card(new BigDecimal("20.00"), "USD", day)));

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
    assertThat(bucket.transactionCount()).isEqualTo(3);
    assertThat(bucket.amountByCurrency().get("EUR")).isEqualByComparingTo("15.00");
    assertThat(bucket.amountByCurrency().get("USD")).isEqualByComparingTo("20.00");
  }

  private CardActivity card(BigDecimal amount, String currency, Instant createdAt) {
    return new CardActivity(
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
        null);
  }
}

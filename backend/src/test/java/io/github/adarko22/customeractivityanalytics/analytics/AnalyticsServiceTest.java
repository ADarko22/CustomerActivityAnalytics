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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            cryptoActivityRepository,
            defaultRangeProperties());
  }

  private static AnalyticsRangeProperties defaultRangeProperties() {
    return new AnalyticsRangeProperties(
        Map.of(
            Granularity.DAY,
                new AnalyticsRangeProperties.Bound(1, ChronoUnit.DAYS, 1, ChronoUnit.MONTHS),
            Granularity.WEEK,
                new AnalyticsRangeProperties.Bound(1, ChronoUnit.WEEKS, 30, ChronoUnit.WEEKS),
            Granularity.MONTH,
                new AnalyticsRangeProperties.Bound(1, ChronoUnit.MONTHS, 2, ChronoUnit.YEARS),
            Granularity.YEAR,
                new AnalyticsRangeProperties.Bound(1, ChronoUnit.YEARS, 5, ChronoUnit.YEARS)));
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
  void rejectedRangeCarriesStructuredExtensionProperties() {
    Instant from = Instant.parse("2015-12-31T00:00:00Z");
    Instant to = Instant.parse("2026-09-02T00:00:00Z");

    assertThatThrownBy(
            () ->
                analyticsService.findTimeSeries(
                    customerId,
                    null,
                    null,
                    from,
                    to,
                    null,
                    null,
                    null,
                    noFilters,
                    Granularity.YEAR))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex -> {
              Map<String, Object> properties =
                  ((ResponseStatusException) ex).getBody().getProperties();
              assertThat(properties).containsEntry("granularity", Granularity.YEAR);
              assertThat(properties).containsEntry("minAmount", 1L);
              assertThat(properties).containsEntry("minUnit", "YEARS");
              assertThat(properties).containsEntry("maxAmount", 5L);
              assertThat(properties).containsEntry("maxUnit", "YEARS");
              assertThat(((ResponseStatusException) ex).getBody().getDetail())
                  .contains("YEAR")
                  .contains("1 years")
                  .contains("5 years");
            });
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
  void defaultsToStartOfMonthByDayWhenRangeOmitted() {
    when(transactionRepository.findTopByCustomerIdOrderByCreatedAtDesc(customerId))
        .thenReturn(Optional.empty());
    when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());

    AnalyticsTimeSeriesDto series =
        analyticsService.findTimeSeries(
            customerId, null, null, null, null, null, null, null, noFilters, Granularity.DAY);

    assertThat(series.from()).isNotNull();
    assertThat(series.to()).isNotNull();
    assertThat(series.buckets()).isNotEmpty();
  }

  @Test
  void defaultsRangeToCustomersLatestActivityWhenOmitted() {
    Instant latestActivity = Instant.now().minus(200, ChronoUnit.DAYS);
    when(transactionRepository.findTopByCustomerIdOrderByCreatedAtDesc(customerId))
        .thenReturn(Optional.of(card(new BigDecimal("10.00"), "EUR", latestActivity)));
    when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());

    AnalyticsTimeSeriesDto series =
        analyticsService.findTimeSeries(
            customerId, null, null, null, null, null, null, null, noFilters, Granularity.DAY);

    assertThat(series.to()).isEqualTo(latestActivity);
  }

  @Test
  void defaultsFromToStartOfCurrentMonthWhenRangeOmitted() {
    Instant latestActivity = Instant.parse("2026-02-13T00:00:00Z");
    when(transactionRepository.findTopByCustomerIdOrderByCreatedAtDesc(customerId))
        .thenReturn(Optional.of(card(new BigDecimal("10.00"), "EUR", latestActivity)));
    when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());

    AnalyticsTimeSeriesDto series =
        analyticsService.findTimeSeries(
            customerId, null, null, null, null, null, null, null, noFilters, Granularity.DAY);

    assertThat(series.to()).isEqualTo(latestActivity);
    assertThat(series.from()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
  }

  @Test
  void clampsStartOfMonthDefaultWhenTooCloseToMinimumSpan() {
    Instant latestActivity = Instant.parse("2026-03-01T00:00:00Z");
    when(transactionRepository.findTopByCustomerIdOrderByCreatedAtDesc(customerId))
        .thenReturn(Optional.of(card(new BigDecimal("10.00"), "EUR", latestActivity)));
    when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());

    AnalyticsTimeSeriesDto series =
        analyticsService.findTimeSeries(
            customerId, null, null, null, null, null, null, null, noFilters, Granularity.DAY);

    assertThat(series.to()).isEqualTo(latestActivity);
    assertThat(series.from()).isEqualTo(Instant.parse("2026-02-28T00:00:00Z"));
  }

  @Test
  void fromOnlyDefaultsToUsingGranularitysMaxSpan() {
    Instant from = Instant.parse("2026-08-03T00:00:00Z");
    when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());

    AnalyticsTimeSeriesDto series =
        analyticsService.findTimeSeries(
            customerId, null, null, from, null, null, null, null, noFilters, Granularity.DAY);

    assertThat(series.from()).isEqualTo(from);
    assertThat(series.to()).isEqualTo(Instant.parse("2026-09-03T00:00:00Z"));
  }

  @Test
  void fromOnlyClampsDefaultToTodayWhenMaxSpanWouldBeInTheFuture() {
    Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant from = todayStart.minus(3, ChronoUnit.DAYS);
    when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());

    AnalyticsTimeSeriesDto series =
        analyticsService.findTimeSeries(
            customerId, null, null, from, null, null, null, null, noFilters, Granularity.DAY);

    assertThat(series.from()).isEqualTo(from);
    assertThat(series.to()).isEqualTo(todayStart);
  }

  @Test
  void toOnlyDefaultsFromUsingGranularitysMaxSpan() {
    Instant to = Instant.parse("2026-09-03T00:00:00Z");
    when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());

    AnalyticsTimeSeriesDto series =
        analyticsService.findTimeSeries(
            customerId, null, null, null, to, null, null, null, noFilters, Granularity.DAY);

    assertThat(series.to()).isEqualTo(to);
    assertThat(series.from()).isEqualTo(Instant.parse("2026-08-03T00:00:00Z"));
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

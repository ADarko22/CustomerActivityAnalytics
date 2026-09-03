package io.github.adarko22.customeractivityanalytics.analytics;

import io.github.adarko22.customeractivityanalytics.analytics.dto.AnalyticsBucketDto;
import io.github.adarko22.customeractivityanalytics.analytics.dto.AnalyticsTimeSeriesDto;
import io.github.adarko22.customeractivityanalytics.customer.CustomerService;
import io.github.adarko22.customeractivityanalytics.transaction.ActivityType;
import io.github.adarko22.customeractivityanalytics.transaction.Transaction;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionRepository;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionSpecifications;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionTypeFilters;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivityRepository;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivitySpecifications;
import io.github.adarko22.customeractivityanalytics.transaction.crypto.CryptoActivityRepository;
import io.github.adarko22.customeractivityanalytics.transaction.crypto.CryptoActivitySpecifications;
import io.github.adarko22.customeractivityanalytics.transaction.payment.PaymentActivityRepository;
import io.github.adarko22.customeractivityanalytics.transaction.payment.PaymentActivitySpecifications;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Buckets a customer's already-filtered transactions into a time series (count +
 * amount-sum-by-currency per bucket), in memory rather than via a Postgres {@code GROUP BY
 * date_trunc(...)}. See {@code docs/development/PHASE_3_SCALING_NOTES.md} for why this is
 * sufficient today and what to replace it with once it isn't.
 */
@Service
public class AnalyticsService {

  private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
  private static final ZoneOffset UTC = ZoneOffset.UTC;

  private final CustomerService customerService;
  private final TransactionRepository transactionRepository;
  private final CardActivityRepository cardActivityRepository;
  private final PaymentActivityRepository paymentActivityRepository;
  private final CryptoActivityRepository cryptoActivityRepository;

  public AnalyticsService(
      CustomerService customerService,
      TransactionRepository transactionRepository,
      CardActivityRepository cardActivityRepository,
      PaymentActivityRepository paymentActivityRepository,
      CryptoActivityRepository cryptoActivityRepository) {
    this.customerService = customerService;
    this.transactionRepository = transactionRepository;
    this.cardActivityRepository = cardActivityRepository;
    this.paymentActivityRepository = paymentActivityRepository;
    this.cryptoActivityRepository = cryptoActivityRepository;
  }

  public AnalyticsTimeSeriesDto findTimeSeries(
      UUID customerId,
      ActivityType activityType,
      TransactionStatus status,
      Instant from,
      Instant to,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String currency,
      TransactionTypeFilters typeFilters,
      Granularity granularity) {
    customerService.requireExists(customerId);

    Instant effectiveTo = to != null ? to : Instant.now();
    Instant effectiveFrom =
        from != null ? from : effectiveTo.atZone(UTC).minusMonths(1).toInstant();
    LocalDate fromDate = effectiveFrom.atZone(UTC).toLocalDate();
    LocalDate toDate = effectiveTo.atZone(UTC).toLocalDate();

    if (!granularity.isRangeValid(fromDate, toDate)) {
      log.warn(
          "Rejected analytics range: customerId={}, granularity={}, from={}, to={}",
          customerId,
          granularity,
          fromDate,
          toDate);
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Range [" + fromDate + ", " + toDate + "] is not valid for granularity " + granularity);
    }

    log.debug(
        "Analytics filters: status={}, from={}, to={}, minAmount={}, maxAmount={}, currency={},"
            + " typeFilters={}",
        status,
        effectiveFrom,
        effectiveTo,
        minAmount,
        maxAmount,
        currency,
        typeFilters);

    List<? extends Transaction> rows =
        fetchRows(
            customerId,
            activityType,
            status,
            effectiveFrom,
            effectiveTo,
            minAmount,
            maxAmount,
            currency,
            typeFilters);

    List<AnalyticsBucketDto> buckets =
        bucketize(
            rows, granularity, granularity.bucketStart(fromDate), granularity.bucketStart(toDate));

    log.info(
        "Computed analytics: customerId={}, activityType={}, granularity={}, buckets={}",
        customerId,
        activityType,
        granularity,
        buckets.size());

    return new AnalyticsTimeSeriesDto(
        activityType, granularity, effectiveFrom, effectiveTo, buckets);
  }

  private List<? extends Transaction> fetchRows(
      UUID customerId,
      ActivityType activityType,
      TransactionStatus status,
      Instant from,
      Instant to,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String currency,
      TransactionTypeFilters typeFilters) {
    if (activityType == null) {
      return transactionRepository.findAll(
          TransactionSpecifications.<Transaction>common(
              customerId, status, from, to, minAmount, maxAmount, currency));
    }
    return switch (activityType) {
      case CARD ->
          cardActivityRepository.findAll(
              CardActivitySpecifications.filter(
                  customerId,
                  status,
                  from,
                  to,
                  minAmount,
                  maxAmount,
                  currency,
                  typeFilters.cardType(),
                  typeFilters.merchantName(),
                  typeFilters.mccCode(),
                  typeFilters.cardPresent()));
      case PAYMENT ->
          paymentActivityRepository.findAll(
              PaymentActivitySpecifications.filter(
                  customerId,
                  status,
                  from,
                  to,
                  minAmount,
                  maxAmount,
                  currency,
                  typeFilters.paymentMethod(),
                  typeFilters.senderAccount(),
                  typeFilters.receiverAccount(),
                  typeFilters.receiverBankCountry()));
      case CRYPTO ->
          cryptoActivityRepository.findAll(
              CryptoActivitySpecifications.filter(
                  customerId,
                  status,
                  from,
                  to,
                  minAmount,
                  maxAmount,
                  currency,
                  typeFilters.blockchain(),
                  typeFilters.walletAddressFrom(),
                  typeFilters.walletAddressTo(),
                  typeFilters.exchangeName()));
    };
  }

  private List<AnalyticsBucketDto> bucketize(
      List<? extends Transaction> rows,
      Granularity granularity,
      LocalDate firstBucket,
      LocalDate lastBucket) {
    Map<LocalDate, List<Transaction>> byBucket = new HashMap<>();
    for (Transaction row : rows) {
      LocalDate bucket = granularity.bucketStart(row.getCreatedAt().atZone(UTC).toLocalDate());
      byBucket.computeIfAbsent(bucket, key -> new ArrayList<>()).add(row);
    }

    List<AnalyticsBucketDto> buckets = new ArrayList<>();
    for (LocalDate cursor = firstBucket;
        !cursor.isAfter(lastBucket);
        cursor = granularity.next(cursor)) {
      List<Transaction> bucketRows = byBucket.getOrDefault(cursor, List.of());
      Map<String, BigDecimal> amountByCurrency = new TreeMap<>();
      for (Transaction row : bucketRows) {
        amountByCurrency.merge(row.getCurrency(), row.getAmount(), BigDecimal::add);
      }
      buckets.add(
          new AnalyticsBucketDto(
              cursor.atStartOfDay(UTC).toInstant(), bucketRows.size(), amountByCurrency));
    }
    return buckets;
  }
}

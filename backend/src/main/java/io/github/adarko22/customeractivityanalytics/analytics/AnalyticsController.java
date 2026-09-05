package io.github.adarko22.customeractivityanalytics.analytics;

import io.github.adarko22.customeractivityanalytics.analytics.dto.AnalyticsTimeSeriesDto;
import io.github.adarko22.customeractivityanalytics.transaction.ActivityType;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionCommonFilters;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionTypeFilters;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the customer analytics time-series endpoint, filterable the same way as the transaction
 * overview.
 */
@RestController
public class AnalyticsController {

  private final AnalyticsService analyticsService;

  public AnalyticsController(AnalyticsService analyticsService) {
    this.analyticsService = analyticsService;
  }

  @GetMapping("/api/v1/customers/{customerId}/analytics")
  public AnalyticsTimeSeriesDto findTimeSeries(
      @PathVariable UUID customerId,
      @RequestParam(required = false) ActivityType activityType,
      @RequestParam(required = false) TransactionStatus status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) BigDecimal minAmount,
      @RequestParam(required = false) BigDecimal maxAmount,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) String cardType,
      @RequestParam(required = false) String merchantName,
      @RequestParam(required = false) String mccCode,
      @RequestParam(required = false) Boolean cardPresent,
      @RequestParam(required = false) String paymentMethod,
      @RequestParam(required = false) String senderAccount,
      @RequestParam(required = false) String receiverAccount,
      @RequestParam(required = false) String receiverBankCountry,
      @RequestParam(required = false) String blockchain,
      @RequestParam(required = false) String walletAddressFrom,
      @RequestParam(required = false) String walletAddressTo,
      @RequestParam(required = false) String exchangeName,
      @RequestParam(defaultValue = "DAY") Granularity granularity) {
    TransactionTypeFilters typeFilters =
        new TransactionTypeFilters(
            cardType,
            merchantName,
            mccCode,
            cardPresent,
            paymentMethod,
            senderAccount,
            receiverAccount,
            receiverBankCountry,
            blockchain,
            walletAddressFrom,
            walletAddressTo,
            exchangeName);
    TransactionCommonFilters filters =
        new TransactionCommonFilters(status, from, to, minAmount, maxAmount, currency);
    return analyticsService.findTimeSeries(
        customerId, activityType, filters, typeFilters, granularity);
  }
}

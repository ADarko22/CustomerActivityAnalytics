package io.github.adarko22.customeractivityanalytics.transaction;

import io.github.adarko22.customeractivityanalytics.transaction.dto.TransactionDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Paginated, filterable transaction overview and per-transaction detail, one endpoint each. */
@RestController
public class TransactionController {

  private final TransactionService transactionService;

  public TransactionController(TransactionService transactionService) {
    this.transactionService = transactionService;
  }

  @GetMapping("/api/v1/customers/{customerId}/transactions")
  public Page<TransactionDto> findOverview(
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
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
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
    return transactionService.findOverview(
        customerId, activityType, filters, typeFilters, pageable);
  }

  @GetMapping("/api/v1/customers/{customerId}/transactions/{transactionId}")
  public TransactionDto findDetail(
      @PathVariable UUID customerId, @PathVariable UUID transactionId) {
    return transactionService.findDetail(customerId, transactionId);
  }
}

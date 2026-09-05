package io.github.adarko22.customeractivityanalytics.transaction;

import io.github.adarko22.customeractivityanalytics.customer.CustomerService;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivityRepository;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivitySpecifications;
import io.github.adarko22.customeractivityanalytics.transaction.crypto.CryptoActivityRepository;
import io.github.adarko22.customeractivityanalytics.transaction.crypto.CryptoActivitySpecifications;
import io.github.adarko22.customeractivityanalytics.transaction.dto.TransactionDto;
import io.github.adarko22.customeractivityanalytics.transaction.dto.TransactionMapper;
import io.github.adarko22.customeractivityanalytics.transaction.payment.PaymentActivityRepository;
import io.github.adarko22.customeractivityanalytics.transaction.payment.PaymentActivitySpecifications;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Paginated, filterable, sortable overview and per-transaction detail across all activity types.
 */
@Service
public class TransactionService {

  private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

  private static final Set<String> COMMON_SORT_PROPERTIES =
      Set.of("createdAt", "amount", "currency", "status");

  private static final Map<ActivityType, Set<String>> TYPE_SORT_PROPERTIES =
      Map.of(
          ActivityType.CARD,
          Set.of("cardType", "merchantName", "mccCode", "cardPresent"),
          ActivityType.PAYMENT,
          Set.of("paymentMethod", "senderAccount", "receiverAccount", "receiverBankCountry"),
          ActivityType.CRYPTO,
          Set.of("blockchain", "walletAddressFrom", "walletAddressTo", "exchangeName"));

  private final CustomerService customerService;
  private final TransactionRepository transactionRepository;
  private final CardActivityRepository cardActivityRepository;
  private final PaymentActivityRepository paymentActivityRepository;
  private final CryptoActivityRepository cryptoActivityRepository;

  public TransactionService(
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

  public Page<TransactionDto> findOverview(
      UUID customerId,
      ActivityType activityType,
      TransactionCommonFilters filters,
      TransactionTypeFilters typeFilters,
      Pageable pageable) {
    customerService.requireExists(customerId);
    validateSort(pageable.getSort(), activityType);

    log.info(
        "Listing transactions: customerId={}, activityType={}, page={}, size={}",
        customerId,
        activityType,
        pageable.getPageNumber(),
        pageable.getPageSize());
    log.debug(
        "Transaction filters: status={}, from={}, to={}, minAmount={}, maxAmount={}, currency={},"
            + " typeFilters={}",
        filters.status(),
        filters.from(),
        filters.to(),
        filters.minAmount(),
        filters.maxAmount(),
        filters.currency(),
        typeFilters);

    if (activityType == null) {
      return transactionRepository
          .findAll(
              TransactionSpecifications.<Transaction>common(
                  customerId,
                  filters.status(),
                  filters.from(),
                  filters.to(),
                  filters.minAmount(),
                  filters.maxAmount(),
                  filters.currency()),
              pageable)
          .map(TransactionMapper::toDto);
    }

    return switch (activityType) {
      case CARD ->
          cardActivityRepository
              .findAll(
                  CardActivitySpecifications.filter(
                      customerId,
                      filters,
                      typeFilters.cardType(),
                      typeFilters.merchantName(),
                      typeFilters.mccCode(),
                      typeFilters.cardPresent()),
                  pageable)
              .map(TransactionMapper::toDto);
      case PAYMENT ->
          paymentActivityRepository
              .findAll(
                  PaymentActivitySpecifications.filter(
                      customerId,
                      filters,
                      typeFilters.paymentMethod(),
                      typeFilters.senderAccount(),
                      typeFilters.receiverAccount(),
                      typeFilters.receiverBankCountry()),
                  pageable)
              .map(TransactionMapper::toDto);
      case CRYPTO ->
          cryptoActivityRepository
              .findAll(
                  CryptoActivitySpecifications.filter(
                      customerId,
                      filters,
                      typeFilters.blockchain(),
                      typeFilters.walletAddressFrom(),
                      typeFilters.walletAddressTo(),
                      typeFilters.exchangeName()),
                  pageable)
              .map(TransactionMapper::toDto);
    };
  }

  public TransactionDto findDetail(UUID customerId, UUID transactionId) {
    customerService.requireExists(customerId);
    Transaction transaction =
        transactionRepository
            .findById(transactionId)
            .filter(t -> t.getCustomerId().equals(customerId))
            .orElseThrow(
                () -> {
                  log.warn(
                      "Transaction not found: customerId={}, transactionId={}",
                      customerId,
                      transactionId);
                  return new ResponseStatusException(
                      HttpStatus.NOT_FOUND, "Transaction not found: " + transactionId);
                });
    return TransactionMapper.toDto(transaction);
  }

  private void validateSort(Sort sort, ActivityType activityType) {
    Set<String> allowed =
        activityType == null
            ? COMMON_SORT_PROPERTIES
            : Stream.concat(
                    COMMON_SORT_PROPERTIES.stream(),
                    TYPE_SORT_PROPERTIES.get(activityType).stream())
                .collect(Collectors.toSet());
    for (Sort.Order order : sort) {
      if (!allowed.contains(order.getProperty())) {
        log.warn("Rejected sort property: {}", order.getProperty());
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Unsupported sort property: " + order.getProperty());
      }
    }
  }
}

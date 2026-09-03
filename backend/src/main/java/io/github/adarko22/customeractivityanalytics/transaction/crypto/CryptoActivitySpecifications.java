package io.github.adarko22.customeractivityanalytics.transaction.crypto;

import io.github.adarko22.customeractivityanalytics.transaction.TransactionSpecifications;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class CryptoActivitySpecifications {

  private CryptoActivitySpecifications() {}

  public static Specification<CryptoActivity> filter(
      UUID customerId,
      TransactionStatus status,
      Instant from,
      Instant to,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String currency,
      String blockchain,
      String walletAddressFrom,
      String walletAddressTo,
      String exchangeName) {
    return TransactionSpecifications.<CryptoActivity>common(
            customerId, status, from, to, minAmount, maxAmount, currency)
        .and(typeFilters(blockchain, walletAddressFrom, walletAddressTo, exchangeName));
  }

  private static Specification<CryptoActivity> typeFilters(
      String blockchain, String walletAddressFrom, String walletAddressTo, String exchangeName) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (blockchain != null && !blockchain.isBlank()) {
        predicates.add(cb.equal(cb.upper(root.get("blockchain")), blockchain.toUpperCase()));
      }
      if (walletAddressFrom != null && !walletAddressFrom.isBlank()) {
        predicates.add(
            cb.like(
                cb.lower(root.get("walletAddressFrom")),
                "%" + walletAddressFrom.toLowerCase() + "%"));
      }
      if (walletAddressTo != null && !walletAddressTo.isBlank()) {
        predicates.add(
            cb.like(
                cb.lower(root.get("walletAddressTo")), "%" + walletAddressTo.toLowerCase() + "%"));
      }
      if (exchangeName != null && !exchangeName.isBlank()) {
        predicates.add(
            cb.like(cb.lower(root.get("exchangeName")), "%" + exchangeName.toLowerCase() + "%"));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}

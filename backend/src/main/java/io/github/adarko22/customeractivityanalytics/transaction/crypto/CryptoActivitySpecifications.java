package io.github.adarko22.customeractivityanalytics.transaction.crypto;

import io.github.adarko22.customeractivityanalytics.transaction.TransactionCommonFilters;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionSpecifications;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Query predicates for {@link CryptoActivity}: the shared transaction filters plus crypto-specific
 * fields.
 */
public final class CryptoActivitySpecifications {

  private CryptoActivitySpecifications() {}

  public static Specification<CryptoActivity> filter(
      UUID customerId,
      TransactionCommonFilters filters,
      String blockchain,
      String walletAddressFrom,
      String walletAddressTo,
      String exchangeName) {
    return TransactionSpecifications.<CryptoActivity>common(
            customerId,
            filters.status(),
            filters.from(),
            filters.to(),
            filters.minAmount(),
            filters.maxAmount(),
            filters.currency())
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

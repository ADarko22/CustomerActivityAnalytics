package io.github.adarko22.customeractivityanalytics.transaction.payment;

import io.github.adarko22.customeractivityanalytics.transaction.TransactionCommonFilters;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionSpecifications;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Query predicates for {@link PaymentActivity}: the shared transaction filters plus
 * payment-specific fields.
 */
public final class PaymentActivitySpecifications {

  private PaymentActivitySpecifications() {}

  public static Specification<PaymentActivity> filter(
      UUID customerId,
      TransactionCommonFilters filters,
      String paymentMethod,
      String senderAccount,
      String receiverAccount,
      String receiverBankCountry) {
    return TransactionSpecifications.<PaymentActivity>common(
            customerId,
            filters.status(),
            filters.from(),
            filters.to(),
            filters.minAmount(),
            filters.maxAmount(),
            filters.currency())
        .and(typeFilters(paymentMethod, senderAccount, receiverAccount, receiverBankCountry));
  }

  private static Specification<PaymentActivity> typeFilters(
      String paymentMethod,
      String senderAccount,
      String receiverAccount,
      String receiverBankCountry) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (paymentMethod != null && !paymentMethod.isBlank()) {
        predicates.add(cb.equal(cb.upper(root.get("paymentMethod")), paymentMethod.toUpperCase()));
      }
      if (senderAccount != null && !senderAccount.isBlank()) {
        predicates.add(
            cb.like(cb.lower(root.get("senderAccount")), "%" + senderAccount.toLowerCase() + "%"));
      }
      if (receiverAccount != null && !receiverAccount.isBlank()) {
        predicates.add(
            cb.like(
                cb.lower(root.get("receiverAccount")), "%" + receiverAccount.toLowerCase() + "%"));
      }
      if (receiverBankCountry != null && !receiverBankCountry.isBlank()) {
        predicates.add(
            cb.equal(cb.upper(root.get("receiverBankCountry")), receiverBankCountry.toUpperCase()));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}

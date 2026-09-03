package io.github.adarko22.customeractivityanalytics.transaction.payment;

import io.github.adarko22.customeractivityanalytics.transaction.TransactionSpecifications;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class PaymentActivitySpecifications {

  private PaymentActivitySpecifications() {}

  public static Specification<PaymentActivity> filter(
      UUID customerId,
      TransactionStatus status,
      Instant from,
      Instant to,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String currency,
      String paymentMethod,
      String senderAccount,
      String receiverAccount,
      String receiverBankCountry) {
    return TransactionSpecifications.<PaymentActivity>common(
            customerId, status, from, to, minAmount, maxAmount, currency)
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

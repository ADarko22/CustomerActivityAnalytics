package io.github.adarko22.customeractivityanalytics.transaction;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransactionRepository
    extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

  Optional<Transaction> findTopByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}

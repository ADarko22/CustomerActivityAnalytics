package io.github.adarko22.customeractivityanalytics.transaction.payment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaymentActivityRepository
    extends JpaRepository<PaymentActivity, UUID>, JpaSpecificationExecutor<PaymentActivity> {}

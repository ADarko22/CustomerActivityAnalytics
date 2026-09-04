package io.github.adarko22.customeractivityanalytics.risk.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RiskFinalAssessmentRepository
    extends JpaRepository<RiskFinalAssessment, UUID>,
        JpaSpecificationExecutor<RiskFinalAssessment> {

  List<RiskFinalAssessment> findByTransactionIdOrderByTriggeredAtDesc(
      UUID transactionId, Pageable pageable);
}

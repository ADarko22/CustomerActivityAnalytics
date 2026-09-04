package io.github.adarko22.customeractivityanalytics.risk.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RiskRuleRepository
    extends JpaRepository<RiskRule, UUID>, JpaSpecificationExecutor<RiskRule> {

  List<RiskRule> findByAppliesToIn(Collection<RuleScope> scopes);
}

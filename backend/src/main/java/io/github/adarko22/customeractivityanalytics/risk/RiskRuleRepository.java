package io.github.adarko22.customeractivityanalytics.risk;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskRuleRepository extends JpaRepository<RiskRule, UUID> {

  List<RiskRule> findByAppliesToIn(Collection<RuleScope> scopes);
}

package io.github.adarko22.customeractivityanalytics.risk.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskRuleRepository extends JpaRepository<RiskRule, UUID> {

  List<RiskRule> findByAppliesToIn(Collection<RuleScope> scopes);

  Page<RiskRule> findByAppliesTo(RuleScope appliesTo, Pageable pageable);
}

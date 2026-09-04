package io.github.adarko22.customeractivityanalytics.risk.engine;

import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskRule;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskRuleRepository;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RuleScope;
import io.github.adarko22.customeractivityanalytics.transaction.ActivityType;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * RAG source #1 — the risk rules applicable to a transaction's activity type (structured DB
 * filtering, not vector search; see docs/DECISIONS.md D17).
 */
@Service
public class RiskRuleRetrievalService {

  private final RiskRuleRepository riskRuleRepository;

  public RiskRuleRetrievalService(RiskRuleRepository riskRuleRepository) {
    this.riskRuleRepository = riskRuleRepository;
  }

  public List<RiskRule> findApplicable(ActivityType activityType) {
    RuleScope scope = RuleScope.valueOf(activityType.name());
    return riskRuleRepository.findByAppliesToIn(EnumSet.of(scope, RuleScope.ALL));
  }
}

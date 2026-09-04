package io.github.adarko22.customeractivityanalytics.risk;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiskAssessmentLineItemRepository
    extends JpaRepository<RiskAssessmentLineItem, RiskAssessmentLineItemId> {

  @Query(
      """
      select new io.github.adarko22.customeractivityanalytics.risk.RuleContributionRow(
          li.id.assessmentId, li.id.ruleId, r.ruleName, li.scoreContribution)
      from RiskAssessmentLineItem li join RiskRule r on r.ruleId = li.id.ruleId
      where li.id.assessmentId in :assessmentIds
      """)
  List<RuleContributionRow> findByAssessmentIdInWithRuleName(
      @Param("assessmentIds") Collection<UUID> assessmentIds);
}

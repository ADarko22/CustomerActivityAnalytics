package io.github.adarko22.customeractivityanalytics.risk.ai;

import java.util.List;

/** The model's structured response: which rules it judged as matching, plus its narrative. */
public record ModelAssessmentResult(
    List<RuleMatch> ruleMatches, String findings, String recommendations) {}

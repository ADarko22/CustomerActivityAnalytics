package io.github.adarko22.customeractivityanalytics.risk.persistence;

import io.github.adarko22.customeractivityanalytics.transaction.ActivityType;

/** {@code risk_rules.applies_to} — {@link ActivityType} plus {@code ALL}, a risk-only concept. */
public enum RuleScope {
  CARD,
  PAYMENT,
  CRYPTO,
  ALL;

  public boolean matches(ActivityType activityType) {
    return this == ALL || this.name().equals(activityType.name());
  }
}

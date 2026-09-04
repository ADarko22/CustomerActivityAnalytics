-- Phase 4: risk rules, and the two-table persisted assessment model (docs/DECISIONS.md D6).

CREATE TABLE risk_rules (
    rule_id         UUID PRIMARY KEY,
    rule_name       VARCHAR(255) NOT NULL,
    applies_to      VARCHAR(20) NOT NULL CHECK (applies_to IN ('CARD', 'PAYMENT', 'CRYPTO', 'ALL')),
    threshold_logic TEXT NOT NULL,
    weight          DECIMAL(5, 2) NOT NULL
);

CREATE TABLE risk_final_assessments (
    assessment_id   UUID PRIMARY KEY,
    transaction_id  UUID NOT NULL REFERENCES transactions (transaction_id),
    triggered_at    TIMESTAMP NOT NULL,
    risk_level      VARCHAR(10) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    risk_score      DECIMAL(10, 2) NOT NULL,
    findings        TEXT NOT NULL,
    recommendations TEXT NOT NULL
);

CREATE INDEX idx_risk_final_assessments_transaction_id ON risk_final_assessments (transaction_id);
CREATE INDEX idx_risk_final_assessments_triggered_at ON risk_final_assessments (triggered_at);

CREATE TABLE risk_assessments (
    assessment_id       UUID NOT NULL REFERENCES risk_final_assessments (assessment_id),
    rule_id             UUID NOT NULL REFERENCES risk_rules (rule_id),
    transaction_id      UUID NOT NULL REFERENCES transactions (transaction_id),
    triggered_at        TIMESTAMP NOT NULL,
    score_contribution  DECIMAL(5, 2) NOT NULL CHECK (score_contribution >= 0),
    PRIMARY KEY (assessment_id, rule_id)
);

CREATE INDEX idx_risk_assessments_transaction_id ON risk_assessments (transaction_id);

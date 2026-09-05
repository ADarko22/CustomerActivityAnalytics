-- Phase 5 EXT_2: risk_level is computed on read from risk_score (docs/DECISIONS.md D23), no longer stored.
ALTER TABLE risk_final_assessments DROP COLUMN risk_level;

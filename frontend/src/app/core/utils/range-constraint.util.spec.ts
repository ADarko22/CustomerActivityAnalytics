import { RangeConstraint } from '../models/analytics.model';
import {
  addUnit,
  isWithinConstraint,
  maxSelectableTo,
  minSelectableTo,
} from './range-constraint.util';

describe('range-constraint.util', () => {
  describe('addUnit', () => {
    it('adds days', () => {
      expect(addUnit(new Date(2026, 0, 15), 1, 'DAYS')).toEqual(new Date(2026, 0, 16));
    });

    it('adds weeks', () => {
      expect(addUnit(new Date(2026, 0, 15), 2, 'WEEKS')).toEqual(new Date(2026, 0, 29));
    });

    it('adds months, clamping month-end overflow like LocalDate.plusMonths', () => {
      // Jan 31 + 1 month must clamp to Feb 28 (2026 is not a leap year), not overflow into March.
      expect(addUnit(new Date(2026, 0, 31), 1, 'MONTHS')).toEqual(new Date(2026, 1, 28));
    });

    it('adds years, clamping Feb 29 in a non-leap target year', () => {
      expect(addUnit(new Date(2024, 1, 29), 1, 'YEARS')).toEqual(new Date(2025, 1, 28));
    });
  });

  describe('DAY bound (min 1 day, max 1 month) — mirrors the backend boundary test', () => {
    const bound: RangeConstraint = {
      minAmount: 1,
      minUnit: 'DAYS',
      maxAmount: 1,
      maxUnit: 'MONTHS',
    };
    const from = new Date(2026, 0, 15);

    it('rejects a same-day range', () => {
      expect(isWithinConstraint(from, from, bound)).toBeFalse();
    });

    it('accepts exactly 1 day', () => {
      expect(isWithinConstraint(from, addUnit(from, 1, 'DAYS'), bound)).toBeTrue();
    });

    it('accepts exactly 1 month', () => {
      expect(isWithinConstraint(from, addUnit(from, 1, 'MONTHS'), bound)).toBeTrue();
    });

    it('rejects 1 month plus 1 day', () => {
      const to = addUnit(addUnit(from, 1, 'MONTHS'), 1, 'DAYS');
      expect(isWithinConstraint(from, to, bound)).toBeFalse();
    });
  });

  describe('minSelectableTo / maxSelectableTo', () => {
    it('compute the pickable To window from a From date and a constraint', () => {
      const from = new Date(2026, 0, 15);
      const bound: RangeConstraint = {
        minAmount: 1,
        minUnit: 'WEEKS',
        maxAmount: 30,
        maxUnit: 'WEEKS',
      };

      expect(minSelectableTo(from, bound)).toEqual(new Date(2026, 0, 22));
      expect(maxSelectableTo(from, bound)).toEqual(addUnit(from, 30, 'WEEKS'));
    });
  });
});

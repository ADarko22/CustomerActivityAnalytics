import { ChronoUnit, RangeConstraint } from '../models/analytics.model';

/**
 * Adds `amount` of `unit` to `date`, mirroring `java.time.LocalDate.plus(amount, ChronoUnit)`'s
 * semantics (including clamping month/year overflow to the last valid day of the target month,
 * e.g. Jan 31 + 1 month = Feb 28) so the frontend's proactive guard agrees with the backend's
 * authoritative check at the exact boundaries.
 */
export function addUnit(date: Date, amount: number, unit: ChronoUnit): Date {
  switch (unit) {
    case 'DAYS':
      return addDays(date, amount);
    case 'WEEKS':
      return addDays(date, amount * 7);
    case 'MONTHS':
      return addMonths(date, amount);
    case 'YEARS':
      return addMonths(date, amount * 12);
  }
}

export function minSelectableTo(from: Date, constraint: RangeConstraint): Date {
  return addUnit(from, constraint.minAmount, constraint.minUnit);
}

export function maxSelectableTo(from: Date, constraint: RangeConstraint): Date {
  return addUnit(from, constraint.maxAmount, constraint.maxUnit);
}

/** Mirrors `LocalDate.minus(amount, ChronoUnit)` — subtracting is adding a negated amount. */
export function subtractUnit(date: Date, amount: number, unit: ChronoUnit): Date {
  return addUnit(date, -amount, unit);
}

export function minSelectableFrom(to: Date, constraint: RangeConstraint): Date {
  return subtractUnit(to, constraint.maxAmount, constraint.maxUnit);
}

export function maxSelectableFrom(to: Date, constraint: RangeConstraint): Date {
  return subtractUnit(to, constraint.minAmount, constraint.minUnit);
}

export function isWithinConstraint(from: Date, to: Date, constraint: RangeConstraint): boolean {
  return to >= minSelectableTo(from, constraint) && to <= maxSelectableTo(from, constraint);
}

function addDays(date: Date, amount: number): Date {
  const result = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  result.setDate(result.getDate() + amount);
  return result;
}

function addMonths(date: Date, amount: number): Date {
  const day = date.getDate();
  const firstOfTargetMonth = new Date(date.getFullYear(), date.getMonth() + amount, 1);
  const daysInTargetMonth = new Date(
    firstOfTargetMonth.getFullYear(),
    firstOfTargetMonth.getMonth() + 1,
    0,
  ).getDate();
  firstOfTargetMonth.setDate(Math.min(day, daysInTargetMonth));
  return firstOfTargetMonth;
}

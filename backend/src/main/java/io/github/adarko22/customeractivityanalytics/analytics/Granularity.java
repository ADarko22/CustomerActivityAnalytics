package io.github.adarko22.customeractivityanalytics.analytics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Time-bucket granularity for the analytics endpoint. Each constant owns its own range-validity
 * check (per PROJECT_SPECIFICATION.md Feature 3's day/week/month/year span constraints) and
 * UTC-based bucketing behavior, avoiding a separate validator/bucketer class.
 */
public enum Granularity {
  DAY {
    @Override
    public boolean isRangeValid(LocalDate from, LocalDate to) {
      return !to.isBefore(from.plusDays(1)) && !to.isAfter(from.plusMonths(1));
    }

    @Override
    public LocalDate bucketStart(LocalDate date) {
      return date;
    }

    @Override
    public LocalDate next(LocalDate bucketStart) {
      return bucketStart.plusDays(1);
    }
  },
  WEEK {
    @Override
    public boolean isRangeValid(LocalDate from, LocalDate to) {
      return !to.isBefore(from.plusWeeks(1)) && !to.isAfter(from.plusWeeks(30));
    }

    @Override
    public LocalDate bucketStart(LocalDate date) {
      return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Override
    public LocalDate next(LocalDate bucketStart) {
      return bucketStart.plusWeeks(1);
    }
  },
  MONTH {
    @Override
    public boolean isRangeValid(LocalDate from, LocalDate to) {
      return !to.isBefore(from.plusMonths(1)) && !to.isAfter(from.plusYears(2));
    }

    @Override
    public LocalDate bucketStart(LocalDate date) {
      return date.withDayOfMonth(1);
    }

    @Override
    public LocalDate next(LocalDate bucketStart) {
      return bucketStart.plusMonths(1);
    }
  },
  YEAR {
    @Override
    public boolean isRangeValid(LocalDate from, LocalDate to) {
      return !to.isBefore(from.plusYears(1)) && !to.isAfter(from.plusYears(5));
    }

    @Override
    public LocalDate bucketStart(LocalDate date) {
      return date.withDayOfYear(1);
    }

    @Override
    public LocalDate next(LocalDate bucketStart) {
      return bucketStart.plusYears(1);
    }
  };

  /**
   * Whether {@code to} falls within this granularity's allowed span, measured from {@code from}.
   */
  public abstract boolean isRangeValid(LocalDate from, LocalDate to);

  /** The start of the bucket that {@code date} falls into. */
  public abstract LocalDate bucketStart(LocalDate date);

  /** The start of the bucket immediately following {@code bucketStart}. */
  public abstract LocalDate next(LocalDate bucketStart);
}

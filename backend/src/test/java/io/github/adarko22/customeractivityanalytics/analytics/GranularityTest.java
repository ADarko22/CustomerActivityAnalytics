package io.github.adarko22.customeractivityanalytics.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GranularityTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 15);

  @Test
  void dayAllowsSpanFromOneDayToOneMonth() {
    assertThat(Granularity.DAY.isRangeValid(FROM, FROM.minusDays(1))).isFalse();
    assertThat(Granularity.DAY.isRangeValid(FROM, FROM)).isFalse();
    assertThat(Granularity.DAY.isRangeValid(FROM, FROM.plusDays(1))).isTrue();
    assertThat(Granularity.DAY.isRangeValid(FROM, FROM.plusMonths(1))).isTrue();
    assertThat(Granularity.DAY.isRangeValid(FROM, FROM.plusMonths(1).plusDays(1))).isFalse();
  }

  @Test
  void weekAllowsSpanFromOneWeekToThirtyWeeks() {
    assertThat(Granularity.WEEK.isRangeValid(FROM, FROM.plusDays(6))).isFalse();
    assertThat(Granularity.WEEK.isRangeValid(FROM, FROM.plusWeeks(1))).isTrue();
    assertThat(Granularity.WEEK.isRangeValid(FROM, FROM.plusWeeks(30))).isTrue();
    assertThat(Granularity.WEEK.isRangeValid(FROM, FROM.plusWeeks(30).plusDays(1))).isFalse();
  }

  @Test
  void monthAllowsSpanFromOneMonthToTwoYears() {
    assertThat(Granularity.MONTH.isRangeValid(FROM, FROM.plusDays(20))).isFalse();
    assertThat(Granularity.MONTH.isRangeValid(FROM, FROM.plusMonths(1))).isTrue();
    assertThat(Granularity.MONTH.isRangeValid(FROM, FROM.plusYears(2))).isTrue();
    assertThat(Granularity.MONTH.isRangeValid(FROM, FROM.plusYears(2).plusDays(1))).isFalse();
  }

  @Test
  void yearAllowsSpanFromOneYearToFiveYears() {
    assertThat(Granularity.YEAR.isRangeValid(FROM, FROM.plusMonths(6))).isFalse();
    assertThat(Granularity.YEAR.isRangeValid(FROM, FROM.plusYears(1))).isTrue();
    assertThat(Granularity.YEAR.isRangeValid(FROM, FROM.plusYears(5))).isTrue();
    assertThat(Granularity.YEAR.isRangeValid(FROM, FROM.plusYears(5).plusDays(1))).isFalse();
  }

  @Test
  void dayBucketStartIsTheDateItself() {
    assertThat(Granularity.DAY.bucketStart(FROM)).isEqualTo(FROM);
    assertThat(Granularity.DAY.next(FROM)).isEqualTo(FROM.plusDays(1));
  }

  @Test
  void weekBucketStartIsTheIsoMonday() {
    // 2026-01-15 is a Thursday; the ISO week starts Monday 2026-01-12.
    assertThat(Granularity.WEEK.bucketStart(FROM)).isEqualTo(LocalDate.of(2026, 1, 12));
    assertThat(Granularity.WEEK.next(LocalDate.of(2026, 1, 12)))
        .isEqualTo(LocalDate.of(2026, 1, 19));
  }

  @Test
  void monthBucketStartIsTheFirstOfMonth() {
    assertThat(Granularity.MONTH.bucketStart(FROM)).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(Granularity.MONTH.next(LocalDate.of(2026, 1, 1)))
        .isEqualTo(LocalDate.of(2026, 2, 1));
  }

  @Test
  void yearBucketStartIsTheFirstOfYear() {
    assertThat(Granularity.YEAR.bucketStart(FROM)).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(Granularity.YEAR.next(LocalDate.of(2026, 1, 1))).isEqualTo(LocalDate.of(2027, 1, 1));
  }
}

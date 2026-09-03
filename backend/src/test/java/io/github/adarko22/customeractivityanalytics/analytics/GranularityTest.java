package io.github.adarko22.customeractivityanalytics.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GranularityTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 15);

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

package io.github.adarko22.customeractivityanalytics.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AnalyticsRangePropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withPropertyValues(
              "app.analytics.range-constraints.bounds.DAY.min-amount=1",
              "app.analytics.range-constraints.bounds.DAY.min-unit=DAYS",
              "app.analytics.range-constraints.bounds.DAY.max-amount=1",
              "app.analytics.range-constraints.bounds.DAY.max-unit=MONTHS",
              "app.analytics.range-constraints.bounds.WEEK.min-amount=1",
              "app.analytics.range-constraints.bounds.WEEK.min-unit=WEEKS",
              "app.analytics.range-constraints.bounds.WEEK.max-amount=30",
              "app.analytics.range-constraints.bounds.WEEK.max-unit=WEEKS",
              "app.analytics.range-constraints.bounds.MONTH.min-amount=1",
              "app.analytics.range-constraints.bounds.MONTH.min-unit=MONTHS",
              "app.analytics.range-constraints.bounds.MONTH.max-amount=2",
              "app.analytics.range-constraints.bounds.MONTH.max-unit=YEARS",
              "app.analytics.range-constraints.bounds.YEAR.min-amount=1",
              "app.analytics.range-constraints.bounds.YEAR.min-unit=YEARS",
              "app.analytics.range-constraints.bounds.YEAR.max-amount=5",
              "app.analytics.range-constraints.bounds.YEAR.max-unit=YEARS");

  @Test
  void bindsDefaultBoundsFromConfiguration() {
    contextRunner.run(
        context -> {
          AnalyticsRangeProperties properties = context.getBean(AnalyticsRangeProperties.class);
          assertThat(properties.boundsFor(Granularity.DAY))
              .isEqualTo(
                  new AnalyticsRangeProperties.Bound(1, ChronoUnit.DAYS, 1, ChronoUnit.MONTHS));
          assertThat(properties.boundsFor(Granularity.WEEK))
              .isEqualTo(
                  new AnalyticsRangeProperties.Bound(1, ChronoUnit.WEEKS, 30, ChronoUnit.WEEKS));
          assertThat(properties.boundsFor(Granularity.MONTH))
              .isEqualTo(
                  new AnalyticsRangeProperties.Bound(1, ChronoUnit.MONTHS, 2, ChronoUnit.YEARS));
          assertThat(properties.boundsFor(Granularity.YEAR))
              .isEqualTo(
                  new AnalyticsRangeProperties.Bound(1, ChronoUnit.YEARS, 5, ChronoUnit.YEARS));
        });
  }

  @Test
  void overridingABoundChangesValidationOutcome() {
    contextRunner
        .withPropertyValues("app.analytics.range-constraints.bounds.YEAR.max-amount=1")
        .run(
            context -> {
              AnalyticsRangeProperties.Bound bound =
                  context.getBean(AnalyticsRangeProperties.class).boundsFor(Granularity.YEAR);
              LocalDate from = LocalDate.of(2020, 1, 1);
              assertThat(bound.isValid(from, from.plusYears(5))).isFalse();
              assertThat(bound.isValid(from, from.plusYears(1))).isTrue();
            });
  }

  @Test
  void failsFastWhenAGranularityIsNotConfigured() {
    new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues(
            "app.analytics.range-constraints.bounds.DAY.min-amount=1",
            "app.analytics.range-constraints.bounds.DAY.min-unit=DAYS",
            "app.analytics.range-constraints.bounds.DAY.max-amount=1",
            "app.analytics.range-constraints.bounds.DAY.max-unit=MONTHS")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void failsFastWhenABoundsMaxSpanIsSmallerThanItsMinSpan() {
    new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues(
            "app.analytics.range-constraints.bounds.DAY.min-amount=1",
            "app.analytics.range-constraints.bounds.DAY.min-unit=MONTHS",
            "app.analytics.range-constraints.bounds.DAY.max-amount=1",
            "app.analytics.range-constraints.bounds.DAY.max-unit=DAYS",
            "app.analytics.range-constraints.bounds.WEEK.min-amount=1",
            "app.analytics.range-constraints.bounds.WEEK.min-unit=WEEKS",
            "app.analytics.range-constraints.bounds.WEEK.max-amount=30",
            "app.analytics.range-constraints.bounds.WEEK.max-unit=WEEKS",
            "app.analytics.range-constraints.bounds.MONTH.min-amount=1",
            "app.analytics.range-constraints.bounds.MONTH.min-unit=MONTHS",
            "app.analytics.range-constraints.bounds.MONTH.max-amount=2",
            "app.analytics.range-constraints.bounds.MONTH.max-unit=YEARS",
            "app.analytics.range-constraints.bounds.YEAR.min-amount=1",
            "app.analytics.range-constraints.bounds.YEAR.min-unit=YEARS",
            "app.analytics.range-constraints.bounds.YEAR.max-amount=5",
            "app.analytics.range-constraints.bounds.YEAR.max-unit=YEARS")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void dayAllowsSpanFromOneDayToOneMonth() {
    contextRunner.run(
        context -> {
          AnalyticsRangeProperties.Bound bound =
              context.getBean(AnalyticsRangeProperties.class).boundsFor(Granularity.DAY);
          LocalDate from = LocalDate.of(2026, 1, 15);
          assertThat(bound.isValid(from, from.minusDays(1))).isFalse();
          assertThat(bound.isValid(from, from)).isFalse();
          assertThat(bound.isValid(from, from.plusDays(1))).isTrue();
          assertThat(bound.isValid(from, from.plusMonths(1))).isTrue();
          assertThat(bound.isValid(from, from.plusMonths(1).plusDays(1))).isFalse();
        });
  }

  @Test
  void weekAllowsSpanFromOneWeekToThirtyWeeks() {
    contextRunner.run(
        context -> {
          AnalyticsRangeProperties.Bound bound =
              context.getBean(AnalyticsRangeProperties.class).boundsFor(Granularity.WEEK);
          LocalDate from = LocalDate.of(2026, 1, 15);
          assertThat(bound.isValid(from, from.plusDays(6))).isFalse();
          assertThat(bound.isValid(from, from.plusWeeks(1))).isTrue();
          assertThat(bound.isValid(from, from.plusWeeks(30))).isTrue();
          assertThat(bound.isValid(from, from.plusWeeks(30).plusDays(1))).isFalse();
        });
  }

  @Test
  void monthAllowsSpanFromOneMonthToTwoYears() {
    contextRunner.run(
        context -> {
          AnalyticsRangeProperties.Bound bound =
              context.getBean(AnalyticsRangeProperties.class).boundsFor(Granularity.MONTH);
          LocalDate from = LocalDate.of(2026, 1, 15);
          assertThat(bound.isValid(from, from.plusDays(20))).isFalse();
          assertThat(bound.isValid(from, from.plusMonths(1))).isTrue();
          assertThat(bound.isValid(from, from.plusYears(2))).isTrue();
          assertThat(bound.isValid(from, from.plusYears(2).plusDays(1))).isFalse();
        });
  }

  @Test
  void yearAllowsSpanFromOneYearToFiveYears() {
    contextRunner.run(
        context -> {
          AnalyticsRangeProperties.Bound bound =
              context.getBean(AnalyticsRangeProperties.class).boundsFor(Granularity.YEAR);
          LocalDate from = LocalDate.of(2026, 1, 15);
          assertThat(bound.isValid(from, from.plusMonths(6))).isFalse();
          assertThat(bound.isValid(from, from.plusYears(1))).isTrue();
          assertThat(bound.isValid(from, from.plusYears(5))).isTrue();
          assertThat(bound.isValid(from, from.plusYears(5).plusDays(1))).isFalse();
        });
  }

  @EnableConfigurationProperties(AnalyticsRangeProperties.class)
  @Configuration
  static class TestConfig {}
}

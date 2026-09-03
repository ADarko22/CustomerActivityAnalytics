import { Component, Input, computed, signal } from '@angular/core';
import { ChartConfiguration, ChartType } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { AnalyticsTimeSeries } from '../../../core/models/analytics.model';

export type AnalyticsMetric = 'count' | 'amount';

const CURRENCY_COLORS = ['#3f51b5', '#e91e63', '#009688', '#ff9800', '#795548', '#607d8b'];
const MIN_BAR_WIDTH_PX = 28;

@Component({
  selector: 'app-analytics-chart',
  standalone: true,
  imports: [BaseChartDirective],
  templateUrl: './analytics-chart.component.html',
  styleUrl: './analytics-chart.component.scss',
})
export class AnalyticsChartComponent {
  private readonly series = signal<AnalyticsTimeSeries | null>(null);
  private readonly metric = signal<AnalyticsMetric>('count');

  @Input() set data(value: AnalyticsTimeSeries | null) {
    this.series.set(value);
  }

  @Input() set metricType(value: AnalyticsMetric) {
    this.metric.set(value);
  }

  readonly chartType = computed<ChartType>(() => (this.metric() === 'count' ? 'bar' : 'line'));

  readonly chartData = computed<ChartConfiguration['data']>(() => {
    const series = this.series();
    if (!series) {
      return { labels: [], datasets: [] };
    }
    const labels = series.buckets.map((bucket) => this.formatLabel(bucket.bucketStart));

    if (this.metric() === 'count') {
      return {
        labels,
        datasets: [
          {
            label: 'Transaction count',
            data: series.buckets.map((bucket) => bucket.transactionCount),
            backgroundColor: CURRENCY_COLORS[0],
          },
        ],
      };
    }

    const currencies = Array.from(
      new Set(series.buckets.flatMap((bucket) => Object.keys(bucket.amountByCurrency))),
    ).sort();

    return {
      labels,
      datasets: currencies.map((currency, index) => ({
        label: currency,
        data: series.buckets.map((bucket) => bucket.amountByCurrency[currency] ?? 0),
        borderColor: CURRENCY_COLORS[index % CURRENCY_COLORS.length],
        backgroundColor: CURRENCY_COLORS[index % CURRENCY_COLORS.length],
        fill: false,
      })),
    };
  });

  readonly chartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
  };

  readonly minWidthPx = computed(() => (this.series()?.buckets.length ?? 0) * MIN_BAR_WIDTH_PX);

  private formatLabel(bucketStart: string): string {
    return bucketStart.slice(0, 10);
  }
}

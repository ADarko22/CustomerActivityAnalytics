import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { AnalyticsTimeSeries } from '../../../core/models/analytics.model';
import { AnalyticsChartComponent } from './analytics-chart.component';

describe('AnalyticsChartComponent', () => {
  let fixture: ComponentFixture<AnalyticsChartComponent>;
  let component: AnalyticsChartComponent;

  const series: AnalyticsTimeSeries = {
    activityType: null,
    granularity: 'DAY',
    from: '2026-01-01T00:00:00Z',
    to: '2026-01-02T00:00:00Z',
    buckets: [
      {
        bucketStart: '2026-01-01T00:00:00Z',
        transactionCount: 3,
        amountByCurrency: { EUR: 15, USD: 20 },
      },
      {
        bucketStart: '2026-01-02T00:00:00Z',
        transactionCount: 0,
        amountByCurrency: {},
      },
    ],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AnalyticsChartComponent],
      providers: [provideCharts(withDefaultRegisterables())],
    });
    fixture = TestBed.createComponent(AnalyticsChartComponent);
    component = fixture.componentInstance;
  });

  it('renders an empty series when no data is set', () => {
    fixture.detectChanges();
    expect(component.chartData().datasets).toEqual([]);
  });

  it('renders a single bar dataset for the count metric', () => {
    component.metricType = 'count';
    component.data = series;
    fixture.detectChanges();

    expect(component.chartType()).toBe('bar');
    expect(component.chartData().datasets).toHaveSize(1);
    expect(component.chartData().datasets[0].data).toEqual([3, 0]);
  });

  it('renders one line series per currency for the amount metric, zero-filling gaps', () => {
    component.metricType = 'amount';
    component.data = series;
    fixture.detectChanges();

    expect(component.chartType()).toBe('line');
    const datasets = component.chartData().datasets;
    expect(datasets.map((d) => d.label).sort()).toEqual(['EUR', 'USD']);
    const eur = datasets.find((d) => d.label === 'EUR');
    expect(eur?.data).toEqual([15, 0]);
  });
});

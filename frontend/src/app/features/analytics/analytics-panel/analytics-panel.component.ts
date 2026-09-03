import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Component,
  Input,
  OnChanges,
  SimpleChanges,
  computed,
  inject,
  signal,
} from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Subject, debounceTime } from 'rxjs';
import { AnalyticsTimeSeries, Granularity } from '../../../core/models/analytics.model';
import { AnalyticsService } from '../../../core/services/analytics.service';
import { ActivityType, TransactionFilter } from '../../../core/models/transaction.model';
import { TYPE_COLUMNS } from '../../transactions/transaction-table/transaction-table.columns';
import {
  AnalyticsChartComponent,
  AnalyticsMetric,
} from '../analytics-chart/analytics-chart.component';

const FILTER_DEBOUNCE_MS = 300;
const STATUS_OPTIONS = ['COMPLETED', 'PENDING', 'FAILED', 'REVERSED'];
const GRANULARITY_OPTIONS: Granularity[] = ['DAY', 'WEEK', 'MONTH', 'YEAR'];

@Component({
  selector: 'app-analytics-panel',
  standalone: true,
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatButtonToggleModule,
    AnalyticsChartComponent,
  ],
  templateUrl: './analytics-panel.component.html',
  styleUrl: './analytics-panel.component.scss',
})
export class AnalyticsPanelComponent implements OnChanges {
  @Input({ required: true }) customerId!: string;

  private readonly analyticsService = inject(AnalyticsService);
  private readonly change$ = new Subject<void>();

  readonly statusOptions = STATUS_OPTIONS;
  readonly granularityOptions = GRANULARITY_OPTIONS;
  readonly typeColumns = TYPE_COLUMNS;

  readonly activityType = signal<ActivityType | 'ALL'>('ALL');
  readonly filters = signal<TransactionFilter>({});
  readonly fromDate = signal<Date | null>(null);
  readonly toDate = signal<Date | null>(null);
  readonly granularity = signal<Granularity>('DAY');
  readonly metric = signal<AnalyticsMetric>('count');

  readonly series = signal<AnalyticsTimeSeries | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly typeSpecificColumns = computed(() => {
    const type = this.activityType();
    return type === 'ALL' ? [] : this.typeColumns[type];
  });

  constructor() {
    this.change$.pipe(debounceTime(FILTER_DEBOUNCE_MS), takeUntilDestroyed()).subscribe(() => {
      this.load();
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['customerId']) {
      this.load();
    }
  }

  onActivityTypeChange(type: ActivityType | 'ALL'): void {
    this.activityType.set(type);
    this.filters.set({});
    this.change$.next();
  }

  onFilterChange(key: string, value: unknown): void {
    this.filters.update((current) => ({ ...current, [key]: value === '' ? undefined : value }));
    this.change$.next();
  }

  onFromDateChange(date: Date | null): void {
    this.fromDate.set(date);
    this.change$.next();
  }

  onToDateChange(date: Date | null): void {
    this.toDate.set(date);
    this.change$.next();
  }

  onGranularityChange(granularity: Granularity): void {
    this.granularity.set(granularity);
    this.change$.next();
  }

  onMetricChange(metric: AnalyticsMetric): void {
    this.metric.set(metric);
  }

  private load(): void {
    const type = this.activityType();
    const filter: TransactionFilter = {
      ...this.filters(),
      activityType: type === 'ALL' ? undefined : type,
      from: this.fromDate()?.toISOString(),
      to: this.toDate()?.toISOString(),
    };
    this.errorMessage.set(null);
    this.analyticsService.findTimeSeries(this.customerId, filter, this.granularity()).subscribe({
      next: (series) => this.series.set(series),
      error: (error: HttpErrorResponse) => {
        this.series.set(null);
        this.errorMessage.set(
          (error.error && error.error.detail) ||
            'Invalid range for the selected granularity. Please adjust the dates.',
        );
      },
    });
  }
}

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
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleXmark, faFilter } from '@fortawesome/free-solid-svg-icons';
import { Subject, debounceTime } from 'rxjs';
import {
  ChronoUnit,
  Granularity,
  RangeConstraints,
  AnalyticsTimeSeries,
} from '../../../core/models/analytics.model';
import { AnalyticsConfigService } from '../../../core/services/analytics-config.service';
import { AnalyticsService } from '../../../core/services/analytics.service';
import { ActivityType, TransactionFilter } from '../../../core/models/transaction.model';
import { TYPE_COLUMNS } from '../../transactions/transaction-table/transaction-table.columns';
import {
  isWithinConstraint,
  maxSelectableFrom,
  maxSelectableTo,
  minSelectableFrom,
  minSelectableTo,
  subtractUnit,
} from '../../../core/utils/range-constraint.util';
import {
  AnalyticsChartComponent,
  AnalyticsMetric,
} from '../analytics-chart/analytics-chart.component';

const FILTER_DEBOUNCE_MS = 300;
const STATUS_OPTIONS = ['COMPLETED', 'PENDING', 'FAILED', 'REVERSED'];
const GRANULARITY_OPTIONS: Granularity[] = ['DAY', 'WEEK', 'MONTH', 'YEAR'];

function minDate(a: Date, b: Date): Date {
  return a < b ? a : b;
}

interface RangeErrorDetail {
  granularity?: Granularity;
  minAmount?: number;
  minUnit?: ChronoUnit;
  maxAmount?: number;
  maxUnit?: ChronoUnit;
  message: string;
}

@Component({
  selector: 'app-analytics-panel',
  standalone: true,
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatButtonModule,
    MatMenuModule,
    MatTooltipModule,
    FaIconComponent,
    AnalyticsChartComponent,
  ],
  templateUrl: './analytics-panel.component.html',
  styleUrl: './analytics-panel.component.scss',
})
export class AnalyticsPanelComponent implements OnChanges {
  @Input({ required: true }) customerId!: string;

  private readonly analyticsService = inject(AnalyticsService);
  private readonly analyticsConfigService = inject(AnalyticsConfigService);
  private readonly change$ = new Subject<void>();

  readonly faFilter = faFilter;
  readonly faCircleXmark = faCircleXmark;

  readonly statusOptions = STATUS_OPTIONS;
  readonly granularityOptions = GRANULARITY_OPTIONS;
  readonly typeColumns = TYPE_COLUMNS;

  readonly activityType = signal<ActivityType | 'ALL'>('ALL');
  readonly filters = signal<TransactionFilter>({});
  readonly fromDate = signal<Date | null>(null);
  readonly toDate = signal<Date | null>(null);
  readonly granularity = signal<Granularity>('DAY');
  readonly metric = signal<AnalyticsMetric>('count');
  readonly rangeConstraints = signal<RangeConstraints | null>(null);

  readonly series = signal<AnalyticsTimeSeries | null>(null);
  readonly errorDetail = signal<RangeErrorDetail | null>(null);

  readonly typeSpecificColumns = computed(() => {
    const type = this.activityType();
    return type === 'ALL' ? [] : this.typeColumns[type];
  });

  readonly hasActiveSecondaryFilters = computed(() => {
    if (this.activityType() !== 'ALL') {
      return true;
    }
    return Object.values(this.filters()).some((value) => value !== undefined && value !== null && value !== '');
  });

  readonly availableGranularities = computed<ReadonlySet<Granularity>>(() => {
    const from = this.fromDate();
    const to = this.toDate();
    const constraints = this.rangeConstraints();
    if (!from || !to || !constraints) {
      return new Set(GRANULARITY_OPTIONS);
    }
    return new Set(
      GRANULARITY_OPTIONS.filter((option) => isWithinConstraint(from, to, constraints[option])),
    );
  });

  readonly toDatepickerMin = computed<Date | null>(() => {
    const from = this.fromDate();
    const constraint = this.rangeConstraints()?.[this.granularity()];
    return from && constraint ? minSelectableTo(from, constraint) : null;
  });

  readonly toDatepickerMax = computed<Date>(() => {
    const from = this.fromDate();
    const constraint = this.rangeConstraints()?.[this.granularity()];
    const today = new Date();
    return from && constraint ? minDate(maxSelectableTo(from, constraint), today) : today;
  });

  readonly fromDatepickerMin = computed<Date | null>(() => {
    const to = this.toDate();
    const constraint = this.rangeConstraints()?.[this.granularity()];
    return to && constraint ? minSelectableFrom(to, constraint) : null;
  });

  readonly fromDatepickerMax = computed<Date>(() => {
    const to = this.toDate();
    const constraint = this.rangeConstraints()?.[this.granularity()];
    const today = new Date();
    if (to && constraint) {
      return minDate(maxSelectableFrom(to, constraint), today);
    }
    return constraint ? subtractUnit(today, constraint.minAmount, constraint.minUnit) : today;
  });

  readonly constraintsTooltip = computed(() => {
    const constraints = this.rangeConstraints();
    if (!constraints) {
      return 'Loading the allowed date ranges…';
    }
    return GRANULARITY_OPTIONS.map((option) => {
      const constraint = constraints[option];
      const label = `${option}:`.padEnd(7);
      return `${label}${constraint.minAmount} ${constraint.minUnit.toLowerCase()} – ${constraint.maxAmount} ${constraint.maxUnit.toLowerCase()}`;
    }).join('\n');
  });

  constructor() {
    this.analyticsConfigService.getRangeConstraints().subscribe((constraints) => {
      this.rangeConstraints.set(constraints);
    });
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
    this.clearToIfNowInvalid();
    this.change$.next();
  }

  onToDateChange(date: Date | null): void {
    this.toDate.set(date);
    this.change$.next();
  }

  onGranularityChange(granularity: Granularity): void {
    this.granularity.set(granularity);
    this.clearToIfNowInvalid();
    this.change$.next();
  }

  onMetricChange(metric: AnalyticsMetric): void {
    this.metric.set(metric);
  }

  private clearToIfNowInvalid(): void {
    const from = this.fromDate();
    const to = this.toDate();
    const constraint = this.rangeConstraints()?.[this.granularity()];
    if (from && to && constraint && !isWithinConstraint(from, to, constraint)) {
      this.toDate.set(null);
    }
  }

  private load(): void {
    const type = this.activityType();
    const filter: TransactionFilter = {
      ...this.filters(),
      activityType: type === 'ALL' ? undefined : type,
      from: this.fromDate()?.toISOString(),
      to: this.toDate()?.toISOString(),
    };
    this.errorDetail.set(null);
    this.analyticsService.findTimeSeries(this.customerId, filter, this.granularity()).subscribe({
      next: (series) => {
        this.series.set(series);
        this.fromDate.set(new Date(series.from));
        this.toDate.set(new Date(series.to));
      },
      error: (error: HttpErrorResponse) => {
        this.series.set(null);
        const body = (error.error ?? {}) as Partial<RangeErrorDetail> & { detail?: string };
        this.errorDetail.set({
          granularity: body.granularity,
          minAmount: body.minAmount,
          minUnit: body.minUnit,
          maxAmount: body.maxAmount,
          maxUnit: body.maxUnit,
          message:
            body.detail ?? 'Invalid range for the selected granularity. Please adjust the dates.',
        });
      },
    });
  }
}

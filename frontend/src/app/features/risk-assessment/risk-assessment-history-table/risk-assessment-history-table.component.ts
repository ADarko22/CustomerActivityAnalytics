import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFilter, faXmark } from '@fortawesome/free-solid-svg-icons';
import { Subject, debounceTime } from 'rxjs';
import {
  AiRiskAssessment,
  AiRiskAssessmentFilter,
} from '../../../core/models/ai-risk-assessment.model';
import { AiRiskAssessmentService } from '../../../core/services/ai-risk-assessment.service';
import { HISTORY_COLUMNS, HistoryColumnDef } from './risk-assessment-history-table.columns';

const DEFAULT_SORT = 'triggeredAt,desc';
const DEFAULT_PAGE_SIZE = 10;
const FILTER_DEBOUNCE_MS = 300;

@Component({
  selector: 'app-risk-assessment-history-table',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatMenuModule,
    MatButtonModule,
    MatChipsModule,
    MatTooltipModule,
    FaIconComponent,
  ],
  templateUrl: './risk-assessment-history-table.component.html',
  styleUrl: './risk-assessment-history-table.component.scss',
})
export class RiskAssessmentHistoryTableComponent implements OnChanges {
  @Input({ required: true }) customerId!: string;
  @Input({ required: true }) transactionId!: string;

  private readonly aiRiskAssessmentService = inject(AiRiskAssessmentService);
  private readonly filterChange$ = new Subject<void>();

  readonly faFilter = faFilter;
  readonly faXmark = faXmark;
  readonly columns: HistoryColumnDef[] = HISTORY_COLUMNS;
  readonly displayedColumns = this.columns.map((column) => column.key);

  readonly filters = signal<AiRiskAssessmentFilter>({});
  readonly fromDateFilter = signal<Date | null>(null);
  readonly toDateFilter = signal<Date | null>(null);
  readonly sort = signal<string | undefined>(DEFAULT_SORT);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  readonly assessments = signal<AiRiskAssessment[]>([]);
  readonly totalElements = signal(0);
  readonly expandedAssessmentId = signal<string | null>(null);

  constructor() {
    this.filterChange$
      .pipe(debounceTime(FILTER_DEBOUNCE_MS), takeUntilDestroyed())
      .subscribe(() => {
        this.load();
      });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['transactionId'] || changes['customerId']) {
      this.pageIndex.set(0);
      this.filters.set({});
      this.fromDateFilter.set(null);
      this.toDateFilter.set(null);
      this.expandedAssessmentId.set(null);
      this.load();
    }
  }

  /** Invoked by the parent trigger component when a new assessment completes. */
  reload(): void {
    this.pageIndex.set(0);
    this.load();
  }

  onFilterChange(key: string, value: unknown): void {
    this.filters.update((current) => ({ ...current, [key]: value === '' ? undefined : value }));
    this.pageIndex.set(0);
    this.filterChange$.next();
  }

  clearFilter(key: string): void {
    this.filters.update((current) => ({ ...current, [key]: undefined }));
    this.pageIndex.set(0);
    this.filterChange$.next();
  }

  clearScoreFilter(minInput: HTMLInputElement, maxInput: HTMLInputElement): void {
    minInput.value = '';
    maxInput.value = '';
    this.filters.update((current) => ({ ...current, minScore: undefined, maxScore: undefined }));
    this.pageIndex.set(0);
    this.filterChange$.next();
  }

  onFromDateFilterChange(date: Date | null): void {
    this.fromDateFilter.set(date);
    this.onFilterChange('from', date?.toISOString());
  }

  onToDateFilterChange(date: Date | null): void {
    this.toDateFilter.set(date);
    this.onFilterChange('to', date?.toISOString());
  }

  clearDateFilter(): void {
    this.fromDateFilter.set(null);
    this.toDateFilter.set(null);
    this.filters.update((current) => ({ ...current, from: undefined, to: undefined }));
    this.pageIndex.set(0);
    this.filterChange$.next();
  }

  isFilterActive(column: HistoryColumnDef): boolean {
    if (column.filterType === 'score') {
      return this.filters().minScore !== undefined || this.filters().maxScore !== undefined;
    }
    if (column.filterType === 'date') {
      return this.filters().from !== undefined || this.filters().to !== undefined;
    }
    const value = (this.filters() as Record<string, unknown>)[column.key];
    return value !== undefined && value !== null && value !== '';
  }

  onSortChange(sort: Sort): void {
    this.sort.set(sort.direction ? `${sort.active},${sort.direction}` : undefined);
    this.load();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  toggleExpand(row: AiRiskAssessment): void {
    this.expandedAssessmentId.set(
      this.expandedAssessmentId() === row.assessmentId ? null : row.assessmentId,
    );
  }

  private load(): void {
    this.aiRiskAssessmentService
      .findHistory(
        this.customerId,
        this.transactionId,
        this.filters(),
        this.pageIndex(),
        this.pageSize(),
        this.sort(),
      )
      .subscribe((page) => {
        this.assessments.set(page.content);
        this.totalElements.set(page.totalElements);
      });
  }
}

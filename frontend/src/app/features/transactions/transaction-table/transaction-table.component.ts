import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, Input, OnChanges, SimpleChanges, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { Sort, MatSortModule } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFilter, faXmark } from '@fortawesome/free-solid-svg-icons';
import { Subject, debounceTime } from 'rxjs';
import { ActivityType, Transaction, TransactionFilter } from '../../../core/models/transaction.model';
import { TransactionService } from '../../../core/services/transaction.service';
import { TransactionDetailComponent } from '../transaction-detail/transaction-detail.component';
import { COMMON_COLUMNS, ColumnDef, TYPE_COLUMNS } from './transaction-table.columns';

const DEFAULT_SORT = 'createdAt,desc';
const DEFAULT_PAGE_SIZE = 20;
const FILTER_DEBOUNCE_MS = 300;

@Component({
  selector: 'app-transaction-table',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatMenuModule,
    MatButtonModule,
    FaIconComponent,
    TransactionDetailComponent,
  ],
  templateUrl: './transaction-table.component.html',
  styleUrl: './transaction-table.component.scss',
})
export class TransactionTableComponent implements OnChanges {
  @Input({ required: true }) customerId!: string;

  private readonly transactionService = inject(TransactionService);
  private readonly filterChange$ = new Subject<void>();

  readonly faFilter = faFilter;
  readonly faXmark = faXmark;

  readonly activityType = signal<ActivityType | 'ALL'>('ALL');
  readonly filters = signal<TransactionFilter>({});
  readonly sort = signal<string | undefined>(DEFAULT_SORT);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  readonly transactions = signal<Transaction[]>([]);
  readonly totalElements = signal(0);
  readonly expandedTransactionId = signal<string | null>(null);

  readonly columns = computed<ColumnDef[]>(() => {
    const type = this.activityType();
    return type === 'ALL' ? COMMON_COLUMNS : [...COMMON_COLUMNS, ...TYPE_COLUMNS[type]];
  });

  readonly displayedColumns = computed(() => this.columns().map((column) => column.key));

  constructor() {
    this.filterChange$.pipe(debounceTime(FILTER_DEBOUNCE_MS), takeUntilDestroyed()).subscribe(() => {
      this.load();
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['customerId']) {
      this.pageIndex.set(0);
      this.load();
    }
  }

  onActivityTypeChange(type: ActivityType | 'ALL'): void {
    this.activityType.set(type);
    this.filters.set({});
    this.pageIndex.set(0);
    this.sort.set(DEFAULT_SORT);
    this.expandedTransactionId.set(null);
    this.load();
  }

  onFilterChange(key: string, value: unknown): void {
    this.filters.update((current) => ({ ...current, [key]: value === '' ? undefined : value }));
    this.pageIndex.set(0);
    this.filterChange$.next();
  }

  clearFilter(key: string, ...inputs: HTMLInputElement[]): void {
    inputs.forEach((input) => (input.value = ''));
    this.filters.update((current) => ({ ...current, [key]: undefined }));
    this.pageIndex.set(0);
    this.filterChange$.next();
  }

  clearAmountFilter(minInput: HTMLInputElement, maxInput: HTMLInputElement): void {
    minInput.value = '';
    maxInput.value = '';
    this.filters.update((current) => ({ ...current, minAmount: undefined, maxAmount: undefined }));
    this.pageIndex.set(0);
    this.filterChange$.next();
  }

  isFilterActive(column: ColumnDef): boolean {
    if (column.filterType === 'amount') {
      return this.filters().minAmount !== undefined || this.filters().maxAmount !== undefined;
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

  toggleExpand(row: Transaction): void {
    this.expandedTransactionId.set(
      this.expandedTransactionId() === row.transactionId ? null : row.transactionId,
    );
  }

  private load(): void {
    const type = this.activityType();
    const filter: TransactionFilter =
      type === 'ALL' ? this.filters() : { ...this.filters(), activityType: type };
    this.transactionService
      .findOverview(this.customerId, filter, this.pageIndex(), this.pageSize(), this.sort())
      .subscribe((page) => {
        this.transactions.set(page.content);
        this.totalElements.set(page.totalElements);
      });
  }
}

import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFilter, faPen, faTrash, faXmark } from '@fortawesome/free-solid-svg-icons';
import { Subject, debounceTime } from 'rxjs';
import { RiskRule, RiskRuleFilter } from '../../../core/models/risk-rule.model';
import { RiskRuleService } from '../../../core/services/risk-rule.service';
import { AuthService } from '../../../core/services/auth.service';
import { RISK_RULE_COLUMNS, RiskRuleColumnDef } from './risk-rules-table.columns';

const DEFAULT_SORT = 'ruleName,asc';
const DEFAULT_PAGE_SIZE = 20;
const FILTER_DEBOUNCE_MS = 300;

@Component({
  selector: 'app-risk-rules-table',
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
    MatChipsModule,
    MatTooltipModule,
    FaIconComponent,
  ],
  templateUrl: './risk-rules-table.component.html',
  styleUrl: './risk-rules-table.component.scss',
})
export class RiskRulesTableComponent {
  private readonly riskRuleService = inject(RiskRuleService);
  private readonly authService = inject(AuthService);
  private readonly filterChange$ = new Subject<void>();

  @Output() edit = new EventEmitter<RiskRule>();
  @Output() deleteRequested = new EventEmitter<RiskRule>();

  readonly faFilter = faFilter;
  readonly faXmark = faXmark;
  readonly faPen = faPen;
  readonly faTrash = faTrash;
  readonly columns: RiskRuleColumnDef[] = RISK_RULE_COLUMNS;
  readonly displayedColumns = this.columns.map((column) => column.key);

  readonly filters = signal<RiskRuleFilter>({});
  readonly sort = signal<string | undefined>(DEFAULT_SORT);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  readonly rules = signal<RiskRule[]>([]);
  readonly totalElements = signal(0);

  constructor() {
    this.filterChange$
      .pipe(debounceTime(FILTER_DEBOUNCE_MS), takeUntilDestroyed())
      .subscribe(() => {
        this.load();
      });
    this.load();
  }

  isAdmin(): boolean {
    return this.authService.isAdmin();
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

  filterValue(key: string): unknown {
    return (this.filters() as Record<string, unknown>)[key];
  }

  clearWeightFilter(minInput: HTMLInputElement, maxInput: HTMLInputElement): void {
    minInput.value = '';
    maxInput.value = '';
    this.filters.update((current) => ({ ...current, minWeight: undefined, maxWeight: undefined }));
    this.pageIndex.set(0);
    this.filterChange$.next();
  }

  isFilterActive(column: RiskRuleColumnDef): boolean {
    if (column.filterType === 'weight') {
      return this.filters().minWeight !== undefined || this.filters().maxWeight !== undefined;
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

  /** Invoked by the parent page after a create/update/delete completes. */
  reload(): void {
    this.load();
  }

  private load(): void {
    this.riskRuleService
      .list(this.filters(), this.pageIndex(), this.pageSize(), this.sort())
      .subscribe((page) => {
        this.rules.set(page.content);
        this.totalElements.set(page.totalElements);
      });
  }
}

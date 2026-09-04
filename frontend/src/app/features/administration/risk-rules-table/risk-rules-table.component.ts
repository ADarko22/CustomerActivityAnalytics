import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPen, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ActivityScope, RiskRule } from '../../../core/models/risk-rule.model';
import { RiskRuleService } from '../../../core/services/risk-rule.service';
import { AuthService } from '../../../core/services/auth.service';

const DEFAULT_SORT = 'ruleName,asc';
const DEFAULT_PAGE_SIZE = 20;

@Component({
  selector: 'app-risk-rules-table',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatSelectModule,
    MatFormFieldModule,
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

  @Output() edit = new EventEmitter<RiskRule>();
  @Output() deleteRequested = new EventEmitter<RiskRule>();

  readonly faPen = faPen;
  readonly faTrash = faTrash;
  readonly scopeOptions: ActivityScope[] = ['CARD', 'PAYMENT', 'CRYPTO', 'ALL'];

  readonly appliesToFilter = signal<ActivityScope | undefined>(undefined);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  readonly rules = signal<RiskRule[]>([]);
  readonly totalElements = signal(0);

  readonly displayedColumns = ['ruleName', 'appliesTo', 'thresholdLogic', 'weight', 'actions'];

  constructor() {
    this.load();
  }

  isAdmin(): boolean {
    return this.authService.isAdmin();
  }

  onFilterChange(appliesTo: ActivityScope | undefined): void {
    this.appliesToFilter.set(appliesTo);
    this.pageIndex.set(0);
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
      .list(this.appliesToFilter(), this.pageIndex(), this.pageSize(), DEFAULT_SORT)
      .subscribe((page) => {
        this.rules.set(page.content);
        this.totalElements.set(page.totalElements);
      });
  }
}

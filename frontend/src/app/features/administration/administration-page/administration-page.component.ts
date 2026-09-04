import { Component, ViewChild, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { RiskRule } from '../../../core/models/risk-rule.model';
import { RiskRuleService } from '../../../core/services/risk-rule.service';
import { AuthService } from '../../../core/services/auth.service';
import { RiskRulesTableComponent } from '../risk-rules-table/risk-rules-table.component';
import {
  RiskRuleFormDialogComponent,
  RiskRuleFormDialogData,
} from '../risk-rule-form-dialog/risk-rule-form-dialog.component';

@Component({
  selector: 'app-administration-page',
  standalone: true,
  imports: [MatButtonModule, FaIconComponent, RiskRulesTableComponent],
  templateUrl: './administration-page.component.html',
  styleUrl: './administration-page.component.scss',
})
export class AdministrationPageComponent {
  private readonly dialog = inject(MatDialog);
  private readonly riskRuleService = inject(RiskRuleService);
  private readonly authService = inject(AuthService);

  @ViewChild(RiskRulesTableComponent) private table?: RiskRulesTableComponent;

  readonly faPlus = faPlus;

  isAdmin(): boolean {
    return this.authService.isAdmin();
  }

  openCreate(): void {
    this.openForm({ rule: null }).subscribe((dto) => {
      if (!dto) {
        return;
      }
      this.riskRuleService.create(dto).subscribe(() => this.table?.reload());
    });
  }

  onEdit(rule: RiskRule): void {
    this.openForm({ rule }).subscribe((dto) => {
      if (!dto) {
        return;
      }
      this.riskRuleService.update(rule.ruleId, dto).subscribe(() => this.table?.reload());
    });
  }

  onDeleteRequested(rule: RiskRule): void {
    if (!window.confirm(`Delete risk rule "${rule.ruleName}"?`)) {
      return;
    }
    this.riskRuleService.delete(rule.ruleId).subscribe(() => this.table?.reload());
  }

  private openForm(data: RiskRuleFormDialogData) {
    return this.dialog
      .open(RiskRuleFormDialogComponent, { data, width: '32rem', maxWidth: '95vw' })
      .afterClosed();
  }
}

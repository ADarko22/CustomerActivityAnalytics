import { CommonModule } from '@angular/common';
import { Component, Input, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faClockRotateLeft } from '@fortawesome/free-solid-svg-icons';
import { Transaction } from '../../../core/models/transaction.model';
import { RiskAssessmentHistoryDialogComponent } from '../../risk-assessment/risk-assessment-history-dialog/risk-assessment-history-dialog.component';
import { RiskAssessmentTriggerComponent } from '../../risk-assessment/risk-assessment-trigger/risk-assessment-trigger.component';

@Component({
  selector: 'app-transaction-detail',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    RiskAssessmentTriggerComponent,
    FaIconComponent,
  ],
  templateUrl: './transaction-detail.component.html',
  styleUrl: './transaction-detail.component.scss',
})
export class TransactionDetailComponent {
  @Input({ required: true }) customerId!: string;
  @Input() transaction: Transaction | null = null;

  private readonly dialog = inject(MatDialog);

  readonly faClockRotateLeft = faClockRotateLeft;

  openHistory(transactionId: string): void {
    this.dialog.open(RiskAssessmentHistoryDialogComponent, {
      data: { customerId: this.customerId, transactionId },
      width: '60%',
      maxWidth: '95vw',
    });
  }
}

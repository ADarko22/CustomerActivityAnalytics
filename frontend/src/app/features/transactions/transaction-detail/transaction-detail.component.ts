import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { Transaction } from '../../../core/models/transaction.model';
import { RiskAssessmentHistoryTableComponent } from '../../risk-assessment/risk-assessment-history-table/risk-assessment-history-table.component';
import { RiskAssessmentTriggerComponent } from '../../risk-assessment/risk-assessment-trigger/risk-assessment-trigger.component';

@Component({
  selector: 'app-transaction-detail',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    RiskAssessmentTriggerComponent,
    RiskAssessmentHistoryTableComponent,
  ],
  templateUrl: './transaction-detail.component.html',
  styleUrl: './transaction-detail.component.scss',
})
export class TransactionDetailComponent {
  @Input({ required: true }) customerId!: string;
  @Input() transaction: Transaction | null = null;
}

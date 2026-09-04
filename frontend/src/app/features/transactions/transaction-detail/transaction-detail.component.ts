import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faClockRotateLeft } from '@fortawesome/free-solid-svg-icons';
import { Transaction } from '../../../core/models/transaction.model';
import { RiskAssessmentTriggerComponent } from '../../risk-assessment/risk-assessment-trigger/risk-assessment-trigger.component';

@Component({
  selector: 'app-transaction-detail',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    RouterLink,
    RiskAssessmentTriggerComponent,
    FaIconComponent,
  ],
  templateUrl: './transaction-detail.component.html',
  styleUrl: './transaction-detail.component.scss',
})
export class TransactionDetailComponent {
  @Input({ required: true }) customerId!: string;
  @Input() transaction: Transaction | null = null;

  readonly faClockRotateLeft = faClockRotateLeft;
}

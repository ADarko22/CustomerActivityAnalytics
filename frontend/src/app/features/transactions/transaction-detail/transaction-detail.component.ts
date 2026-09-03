import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { Transaction } from '../../../core/models/transaction.model';

@Component({
  selector: 'app-transaction-detail',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  templateUrl: './transaction-detail.component.html',
  styleUrl: './transaction-detail.component.scss',
})
export class TransactionDetailComponent {
  @Input() transaction: Transaction | null = null;
}

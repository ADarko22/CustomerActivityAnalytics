import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Transaction } from '../../../core/models/transaction.model';
import { TransactionDetailComponent } from '../transaction-detail/transaction-detail.component';
import { TransactionTableComponent } from '../transaction-table/transaction-table.component';

@Component({
  selector: 'app-transactions-page',
  standalone: true,
  imports: [TransactionTableComponent, TransactionDetailComponent],
  templateUrl: './transactions-page.component.html',
  styleUrl: './transactions-page.component.scss',
})
export class TransactionsPageComponent {
  private readonly route = inject(ActivatedRoute);

  readonly customerId = signal(this.route.snapshot.paramMap.get('customerId') ?? '');
  readonly selectedTransaction = signal<Transaction | null>(null);

  onTransactionSelected(transaction: Transaction): void {
    this.selectedTransaction.set(transaction);
  }
}

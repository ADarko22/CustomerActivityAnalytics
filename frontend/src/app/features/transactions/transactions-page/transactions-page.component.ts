import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TransactionTableComponent } from '../transaction-table/transaction-table.component';

@Component({
  selector: 'app-transactions-page',
  standalone: true,
  imports: [TransactionTableComponent],
  templateUrl: './transactions-page.component.html',
  styleUrl: './transactions-page.component.scss',
})
export class TransactionsPageComponent {
  private readonly route = inject(ActivatedRoute);

  readonly customerId = signal(this.route.snapshot.paramMap.get('customerId') ?? '');
}

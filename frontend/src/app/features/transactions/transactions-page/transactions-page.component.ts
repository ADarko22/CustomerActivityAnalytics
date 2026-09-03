import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { TransactionTableComponent } from '../transaction-table/transaction-table.component';
import { AnalyticsPanelComponent } from '../../analytics/analytics-panel/analytics-panel.component';

@Component({
  selector: 'app-transactions-page',
  standalone: true,
  imports: [MatTabsModule, TransactionTableComponent, AnalyticsPanelComponent],
  templateUrl: './transactions-page.component.html',
  styleUrl: './transactions-page.component.scss',
})
export class TransactionsPageComponent {
  private readonly route = inject(ActivatedRoute);

  readonly customerId = signal(this.route.snapshot.paramMap.get('customerId') ?? '');
}

import { Routes } from '@angular/router';
import { EmptyStateComponent } from './features/empty-state/empty-state.component';
import { TransactionsPageComponent } from './features/transactions/transactions-page/transactions-page.component';
import { TransactionTableComponent } from './features/transactions/transaction-table/transaction-table.component';
import { AnalyticsPanelComponent } from './features/analytics/analytics-panel/analytics-panel.component';

export const routes: Routes = [
  { path: '', component: EmptyStateComponent },
  {
    path: 'customers/:customerId',
    component: TransactionsPageComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'transactions' },
      { path: 'transactions', component: TransactionTableComponent },
      { path: 'analytics', component: AnalyticsPanelComponent },
    ],
  },
];

import { Routes } from '@angular/router';
import { EmptyStateComponent } from './features/empty-state/empty-state.component';
import { TransactionsPageComponent } from './features/transactions/transactions-page/transactions-page.component';
import { TransactionTableComponent } from './features/transactions/transaction-table/transaction-table.component';
import { AnalyticsPanelComponent } from './features/analytics/analytics-panel/analytics-panel.component';
import { AdministrationPageComponent } from './features/administration/administration-page/administration-page.component';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';

export const routes: Routes = [
  { path: '', component: EmptyStateComponent, canActivate: [authGuard] },
  {
    path: 'customers/:customerId',
    component: TransactionsPageComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'transactions' },
      { path: 'transactions', component: TransactionTableComponent },
      { path: 'analytics', component: AnalyticsPanelComponent },
    ],
  },
  {
    path: 'administration',
    component: AdministrationPageComponent,
    canActivate: [authGuard, adminGuard],
  },
];

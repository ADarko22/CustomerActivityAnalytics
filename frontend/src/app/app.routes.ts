import { Routes } from '@angular/router';
import { EmptyStateComponent } from './features/empty-state/empty-state.component';
import { TransactionsPageComponent } from './features/transactions/transactions-page/transactions-page.component';

export const routes: Routes = [
  { path: '', component: EmptyStateComponent },
  { path: 'customers/:customerId/transactions', component: TransactionsPageComponent },
];

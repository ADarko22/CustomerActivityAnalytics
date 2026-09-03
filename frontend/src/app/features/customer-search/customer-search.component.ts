import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { Customer } from '../../core/models/customer.model';
import { CustomerService } from '../../core/services/customer.service';

const SUGGESTION_SIZE = 5;
const DEBOUNCE_MS = 300;

@Component({
  selector: 'app-customer-search',
  standalone: true,
  imports: [ReactiveFormsModule, MatAutocompleteModule, MatFormFieldModule, MatInputModule],
  templateUrl: './customer-search.component.html',
  styleUrl: './customer-search.component.scss',
})
export class CustomerSearchComponent {
  private readonly customerService = inject(CustomerService);
  private readonly router = inject(Router);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly suggestions = signal<Customer[]>([]);

  constructor() {
    this.searchControl.valueChanges
      .pipe(
        debounceTime(DEBOUNCE_MS),
        distinctUntilChanged(),
        switchMap((query) => this.customerService.search(query, 0, SUGGESTION_SIZE)),
        takeUntilDestroyed(),
      )
      .subscribe((page) => this.suggestions.set(page.content));

    this.customerService
      .search('', 0, SUGGESTION_SIZE)
      .subscribe((page) => this.suggestions.set(page.content));
  }

  displayCustomer(customer?: Customer): string {
    return customer ? `${customer.firstName} ${customer.lastName}` : '';
  }

  onCustomerSelected(event: MatAutocompleteSelectedEvent): void {
    const customer = event.option.value as Customer;
    const tab = this.router.url.endsWith('/analytics') ? 'analytics' : 'transactions';
    this.router.navigate(['/customers', customer.customerId, tab]);
  }
}

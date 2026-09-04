import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import {
  EMPTY,
  Subject,
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  of,
  switchMap,
} from 'rxjs';
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
export class CustomerSearchComponent implements OnChanges {
  private readonly customerService = inject(CustomerService);
  private readonly router = inject(Router);
  private readonly customerIdChange$ = new Subject<string | undefined>();

  /** Tracks the customer currently reflected in `searchControl`, to skip a redundant re-fetch
   *  when `customerId` changes to a customer already loaded (notably right after a dropdown
   *  selection, which drives `customerId` via the resulting navigation). */
  private loadedCustomerId?: string;

  @Input() customerId?: string;

  readonly searchControl = new FormControl<string | Customer>('', { nonNullable: true });
  readonly suggestions = signal<Customer[]>([]);

  constructor() {
    this.searchControl.valueChanges
      .pipe(
        // Selecting a suggestion sets the control's raw value to the full `Customer` object
        // (see `displayCustomer`/`onCustomerSelected`) — only a typed string is a search query.
        filter((value): value is string => typeof value === 'string'),
        debounceTime(DEBOUNCE_MS),
        distinctUntilChanged(),
        switchMap((query) => this.customerService.search(query, 0, SUGGESTION_SIZE)),
        takeUntilDestroyed(),
      )
      .subscribe((page) => this.suggestions.set(page.content));

    this.customerService
      .search('', 0, SUGGESTION_SIZE)
      .subscribe((page) => this.suggestions.set(page.content));

    // switchMap cancels a stale in-flight lookup when customerId changes again before it
    // resolves (e.g. rapid navigation between customers), so an out-of-order response can never
    // overwrite the box with a different, no-longer-current customer.
    this.customerIdChange$
      .pipe(
        distinctUntilChanged(),
        switchMap((id) => {
          if (!id) {
            return of(undefined);
          }
          if (id === this.loadedCustomerId) {
            return EMPTY;
          }
          return this.customerService.getById(id).pipe(catchError(() => of(undefined)));
        }),
        takeUntilDestroyed(),
      )
      .subscribe((customer) => {
        this.loadedCustomerId = customer?.customerId;
        this.searchControl.setValue(customer ?? '', { emitEvent: false });
      });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['customerId']) {
      this.customerIdChange$.next(this.customerId);
    }
  }

  displayCustomer(customer?: Customer): string {
    return customer ? `${customer.firstName} ${customer.lastName}` : '';
  }

  onCustomerSelected(event: MatAutocompleteSelectedEvent): void {
    const customer = event.option.value as Customer;
    // Reflect the selection immediately and mark it as already loaded, so the customerId change
    // that the resulting navigation triggers (via AppComponent's routeCustomerId) skips a
    // redundant re-fetch of a customer this component already has in hand.
    this.loadedCustomerId = customer.customerId;
    this.searchControl.setValue(customer, { emitEvent: false });
    const tab = this.router.url.endsWith('/analytics') ? 'analytics' : 'transactions';
    this.router.navigate(['/customers', customer.customerId, tab]);
  }
}

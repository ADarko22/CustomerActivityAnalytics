import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { Router, provideRouter } from '@angular/router';
import { Customer } from '../../core/models/customer.model';
import { CustomerSearchComponent } from './customer-search.component';

describe('CustomerSearchComponent', () => {
  let fixture: ComponentFixture<CustomerSearchComponent>;
  let component: CustomerSearchComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 5 };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CustomerSearchComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(CustomerSearchComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('requests the top 5 alphabetical customers on init', () => {
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/customers' &&
        r.params.get('size') === '5' &&
        r.params.get('query') === '',
    );
    expect(req.request.method).toBe('GET');
    req.flush(emptyPage);
  });

  it('debounces input before searching', fakeAsync(() => {
    httpMock.expectOne((r) => r.params.get('query') === '').flush(emptyPage);

    component.searchControl.setValue('ang');
    tick(299);
    httpMock.expectNone((r) => r.params.get('query') === 'ang');
    tick(1);

    const req = httpMock.expectOne((r) => r.params.get('query') === 'ang');
    req.flush({
      content: [{ customerId: '1', firstName: 'Angelo', lastName: 'Buono' }],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 5,
    });

    expect(component.suggestions().length).toBe(1);
  }));

  it('navigates to the selected customer transactions page', () => {
    httpMock.expectOne((r) => r.params.get('query') === '').flush(emptyPage);
    spyOn(router, 'navigate');

    const customer = { customerId: 'abc-123', firstName: 'Angelo', lastName: 'Buono' };
    component.onCustomerSelected({
      option: { value: customer },
    } as unknown as MatAutocompleteSelectedEvent);

    expect(router.navigate).toHaveBeenCalledWith(['/customers', 'abc-123', 'transactions']);
  });

  it('preserves the analytics tab when switching customers from the analytics route', () => {
    httpMock.expectOne((r) => r.params.get('query') === '').flush(emptyPage);
    spyOnProperty(router, 'url', 'get').and.returnValue('/customers/old-id/analytics');
    spyOn(router, 'navigate');

    const customer = { customerId: 'abc-123', firstName: 'Angelo', lastName: 'Buono' };
    component.onCustomerSelected({
      option: { value: customer },
    } as unknown as MatAutocompleteSelectedEvent);

    expect(router.navigate).toHaveBeenCalledWith(['/customers', 'abc-123', 'analytics']);
  });

  it('populates the search box from the customerId input, without triggering suggestions', () => {
    httpMock.expectOne((r) => r.params.get('query') === '').flush(emptyPage);

    fixture.componentRef.setInput('customerId', 'abc-123');
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/customers/abc-123');
    req.flush({ customerId: 'abc-123', firstName: 'Angelo', lastName: 'Buono' });

    expect(component.searchControl.value as unknown as Customer).toEqual({
      customerId: 'abc-123',
      firstName: 'Angelo',
      lastName: 'Buono',
    });
    httpMock.expectNone((r) => r.url === '/api/v1/customers' && r.params.get('query') !== '');
  });

  it('clears the search box when the customerId input is unset', () => {
    httpMock.expectOne((r) => r.params.get('query') === '').flush(emptyPage);
    fixture.componentRef.setInput('customerId', 'abc-123');
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/v1/customers/abc-123')
      .flush({ customerId: 'abc-123', firstName: 'Angelo', lastName: 'Buono' });

    fixture.componentRef.setInput('customerId', undefined);
    fixture.detectChanges();

    expect(component.searchControl.value).toBe('');
  });

  it('cancels a stale customer lookup when customerId changes again before it resolves', () => {
    httpMock.expectOne((r) => r.params.get('query') === '').flush(emptyPage);

    fixture.componentRef.setInput('customerId', 'stale-id');
    fixture.detectChanges();
    const staleReq = httpMock.expectOne((r) => r.url === '/api/v1/customers/stale-id');

    fixture.componentRef.setInput('customerId', 'fresh-id');
    fixture.detectChanges();
    const freshReq = httpMock.expectOne((r) => r.url === '/api/v1/customers/fresh-id');

    expect(staleReq.cancelled).toBeTrue();

    freshReq.flush({ customerId: 'fresh-id', firstName: 'Fresh', lastName: 'Customer' });

    expect(component.searchControl.value as unknown as Customer).toEqual({
      customerId: 'fresh-id',
      firstName: 'Fresh',
      lastName: 'Customer',
    });
  });

  it('does not re-fetch a customer already selected from the suggestions dropdown', () => {
    httpMock.expectOne((r) => r.params.get('query') === '').flush(emptyPage);
    spyOn(router, 'navigate');

    const customer = { customerId: 'abc-123', firstName: 'Angelo', lastName: 'Buono' };
    component.onCustomerSelected({
      option: { value: customer },
    } as unknown as MatAutocompleteSelectedEvent);

    fixture.componentRef.setInput('customerId', 'abc-123');
    fixture.detectChanges();

    httpMock.expectNone((r) => r.url === '/api/v1/customers/abc-123');
    expect(component.searchControl.value as unknown as Customer).toEqual(customer);
  });

  it('clears the search box gracefully when the customer lookup 404s', () => {
    httpMock.expectOne((r) => r.params.get('query') === '').flush(emptyPage);

    fixture.componentRef.setInput('customerId', 'missing-id');
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === '/api/v1/customers/missing-id')
      .flush('Not found', { status: 404, statusText: 'Not Found' });

    expect(component.searchControl.value).toBe('');
  });
});

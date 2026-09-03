import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { Router, provideRouter } from '@angular/router';
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
});

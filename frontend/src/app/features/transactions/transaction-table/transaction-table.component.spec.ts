import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { TransactionTableComponent } from './transaction-table.component';

describe('TransactionTableComponent', () => {
  let fixture: ComponentFixture<TransactionTableComponent>;
  let component: TransactionTableComponent;
  let httpMock: HttpTestingController;
  const customerId = 'customer-1';
  const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TransactionTableComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(TransactionTableComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('customerId', customerId);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  function overviewUrl(request: { url: string }): boolean {
    return request.url === `/api/v1/customers/${customerId}/transactions`;
  }

  function flushInitial(): void {
    httpMock.expectOne(overviewUrl).flush(emptyPage);
  }

  it('loads the overview with default sort and pagination on init', () => {
    const req = httpMock.expectOne(overviewUrl);
    expect(req.request.params.get('sort')).toBe('createdAt,desc');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush(emptyPage);
  });

  it('requests the new page and size on paginator change', () => {
    flushInitial();

    component.onPageChange({ pageIndex: 2, pageSize: 50 } as PageEvent);

    const req = httpMock.expectOne(overviewUrl);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush(emptyPage);
  });

  it('requests the new sort on sort change', () => {
    flushInitial();

    component.onSortChange({ active: 'amount', direction: 'asc' } as Sort);

    const req = httpMock.expectOne(overviewUrl);
    expect(req.request.params.get('sort')).toBe('amount,asc');
    req.flush(emptyPage);
  });

  it('sends the column filter value as a query param, debounced', fakeAsync(() => {
    flushInitial();

    component.onFilterChange('merchantName', 'Amazon');
    tick(299);
    httpMock.expectNone(overviewUrl);
    tick(1);

    const req = httpMock.expectOne(overviewUrl);
    expect(req.request.params.get('merchantName')).toBe('Amazon');
    req.flush(emptyPage);
  }));

  it('adds type-specific columns and requeries when selecting a type', () => {
    flushInitial();

    component.onActivityTypeChange('CARD');

    expect(component.displayedColumns()).toContain('merchantName');
    const req = httpMock.expectOne(overviewUrl);
    expect(req.request.params.get('activityType')).toBe('CARD');
    req.flush(emptyPage);
  });
});

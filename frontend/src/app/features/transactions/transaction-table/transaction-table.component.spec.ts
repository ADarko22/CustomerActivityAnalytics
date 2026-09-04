import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatMenuTrigger } from '@angular/material/menu';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { CardTransaction } from '../../../core/models/transaction.model';
import { TransactionTableComponent } from './transaction-table.component';

describe('TransactionTableComponent', () => {
  let fixture: ComponentFixture<TransactionTableComponent>;
  let component: TransactionTableComponent;
  let httpMock: HttpTestingController;
  const customerId = 'customer-1';
  const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };

  const card1: CardTransaction = {
    transactionId: 'txn-1',
    customerId,
    activityType: 'CARD',
    amount: 10,
    currency: 'EUR',
    status: 'COMPLETED',
    createdAt: '2026-01-01T00:00:00Z',
    cardPan: '****1234',
    cardType: 'DEBIT',
    merchantName: 'Amazon',
    mccCode: '5732',
    cardPresent: true,
  };
  const card2: CardTransaction = { ...card1, transactionId: 'txn-2' };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TransactionTableComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideNativeDateAdapter(),
      ],
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

  function flushInitial(page: object = emptyPage): void {
    httpMock.expectOne(overviewUrl).flush(page);
    fixture.detectChanges();
  }

  function flushAiAssessmentHistory(): void {
    httpMock
      .expectOne((r) => r.url === `/api/v1/customers/${customerId}/ai-assessments`)
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
  }

  function openFilterMenu(ariaLabel: string): void {
    const button = fixture.debugElement
      .queryAll(By.directive(MatMenuTrigger))
      .find((el) => (el.nativeElement as HTMLElement).getAttribute('aria-label') === ariaLabel);
    button?.injector.get(MatMenuTrigger).openMenu();
    fixture.detectChanges();
  }

  it('loads the overview with default sort and pagination on init', () => {
    const req = httpMock.expectOne(overviewUrl);
    expect(req.request.params.get('sort')).toBe('createdAt,desc');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush(emptyPage);
  });

  it('renders a sort-labeled span and a filter button per filterable column header', () => {
    flushInitial();

    const headerText = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(headerText).toContain('Currency');
    expect(fixture.debugElement.query(By.css('.header-label'))).toBeTruthy();
    expect(fixture.debugElement.query(By.css('.filter-trigger'))).toBeTruthy();
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

  it('opens the filter popover from the header icon and applies the typed value', fakeAsync(() => {
    flushInitial();

    openFilterMenu('Filter Currency');
    const input = document.querySelector('.mat-mdc-menu-panel input') as HTMLInputElement;
    expect(input).toBeTruthy();
    input.value = 'EUR';
    input.dispatchEvent(new Event('input'));
    tick(300);

    const req = httpMock.expectOne(overviewUrl);
    expect(req.request.params.get('currency')).toBe('EUR');
    req.flush(emptyPage);
  }));

  it('stays open when a control inside it is clicked (regression: MatMenu closes on any bubbled click by default)', fakeAsync(() => {
    flushInitial();

    openFilterMenu('Filter Currency');
    const input = document.querySelector('.mat-mdc-menu-panel input') as HTMLInputElement;
    expect(input).toBeTruthy();

    input.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    tick();
    fixture.detectChanges();

    expect(document.querySelector('.mat-mdc-menu-panel'))
      .withContext('the popover must stay open when clicking a control inside it')
      .toBeTruthy();
  }));

  it('clears a select column filter by choosing "Any"', fakeAsync(() => {
    flushInitial();
    component.onFilterChange('status', 'COMPLETED');
    tick(300);
    httpMock.expectOne(overviewUrl).flush(emptyPage);

    component.onFilterChange('status', undefined);
    tick(300);

    const req = httpMock.expectOne(overviewUrl);
    expect(req.request.params.has('status')).toBeFalse();
    req.flush(emptyPage);
  }));

  it('clears a text column filter via the inline Clear control', fakeAsync(() => {
    flushInitial();
    component.onFilterChange('currency', 'EUR');
    tick(300);
    httpMock.expectOne(overviewUrl).flush(emptyPage);

    const fakeInput = document.createElement('input');
    component.clearFilter('currency', fakeInput);
    expect(fakeInput.value).toBe('');
    tick(300);

    const req = httpMock.expectOne(overviewUrl);
    expect(req.request.params.has('currency')).toBeFalse();
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

  it('expands a row to show its detail, and collapses it when clicked again', () => {
    flushInitial({ content: [card1], totalElements: 1, totalPages: 1, number: 0, size: 20 });
    // Every detail row (incl. its nested AI risk-assessment history table) mounts eagerly —
    // `multiTemplateDataRows` renders all row templates per data item, only toggling visibility
    // via CSS — so the history table's request fires immediately, not only once expanded.
    flushAiAssessmentHistory();
    fixture.detectChanges();

    expect(component.expandedTransactionId()).toBeNull();
    const row = fixture.debugElement.queryAll(By.css('tr.transaction-row'))[0];
    const detailRow = fixture.debugElement.queryAll(By.css('tr.detail-row'))[0]
      .nativeElement as HTMLElement;
    expect(detailRow.classList.contains('detail-row-open')).toBeFalse();

    row.triggerEventHandler('click', null);
    fixture.detectChanges();
    expect(component.expandedTransactionId()).toBe('txn-1');
    expect(detailRow.classList.contains('detail-row-open')).toBeTrue();
    expect(detailRow.textContent).toContain('Amazon');

    row.triggerEventHandler('click', null);
    fixture.detectChanges();
    expect(component.expandedTransactionId()).toBeNull();
    expect(detailRow.classList.contains('detail-row-open')).toBeFalse();
  });

  it('expanding a second row collapses the first', () => {
    flushInitial({ content: [card1, card2], totalElements: 2, totalPages: 1, number: 0, size: 20 });
    // Both rows' detail templates mount at once, so both history-table requests are pending
    // simultaneously — match() (not expectOne(), which requires exactly one pending match) then
    // flush each.
    httpMock
      .match((r) => r.url === `/api/v1/customers/${customerId}/ai-assessments`)
      .forEach((req) => req.flush(emptyPage));
    fixture.detectChanges();

    const rows = fixture.debugElement.queryAll(By.css('tr.transaction-row'));
    const detailRows = fixture.debugElement.queryAll(By.css('tr.detail-row'));

    rows[0].triggerEventHandler('click', null);
    fixture.detectChanges();
    expect(
      (detailRows[0].nativeElement as HTMLElement).classList.contains('detail-row-open'),
    ).toBeTrue();
    expect(
      (detailRows[1].nativeElement as HTMLElement).classList.contains('detail-row-open'),
    ).toBeFalse();

    rows[1].triggerEventHandler('click', null);
    fixture.detectChanges();
    expect(
      (detailRows[0].nativeElement as HTMLElement).classList.contains('detail-row-open'),
    ).toBeFalse();
    expect(
      (detailRows[1].nativeElement as HTMLElement).classList.contains('detail-row-open'),
    ).toBeTrue();
  });

  it('filters by a date range from the Date column popover, and clears it', fakeAsync(() => {
    flushInitial();

    openFilterMenu('Filter Date');
    component.onFromDateFilterChange(new Date(2026, 0, 1));
    tick(300);
    httpMock.expectOne(overviewUrl).flush(emptyPage);

    component.onToDateFilterChange(new Date(2026, 0, 31));
    tick(300);
    const req = httpMock.expectOne(overviewUrl);
    expect(req.request.params.get('from')).toBe(new Date(2026, 0, 1).toISOString());
    expect(req.request.params.get('to')).toBe(new Date(2026, 0, 31).toISOString());
    req.flush(emptyPage);

    component.clearDateFilter();
    tick(300);
    const clearedReq = httpMock.expectOne(overviewUrl);
    expect(clearedReq.request.params.has('from')).toBeFalse();
    expect(clearedReq.request.params.has('to')).toBeFalse();
    clearedReq.flush(emptyPage);
  }));

  it('resets filters and activity type when switching customers', fakeAsync(() => {
    flushInitial();

    component.onActivityTypeChange('CARD');
    httpMock.expectOne(overviewUrl).flush(emptyPage);
    component.onFilterChange('currency', 'EUR');
    tick(300);
    httpMock.expectOne(overviewUrl).flush(emptyPage);

    fixture.componentRef.setInput('customerId', 'customer-2');
    fixture.detectChanges();

    expect(component.activityType()).toBe('ALL');
    expect(component.filters()).toEqual({});
    const req = httpMock.expectOne(
      (r: { url: string }) => r.url === '/api/v1/customers/customer-2/transactions',
    );
    expect(req.request.params.has('currency')).toBeFalse();
    req.flush(emptyPage);
  }));
});

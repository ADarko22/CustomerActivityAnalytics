import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatMenuTrigger } from '@angular/material/menu';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { AiRiskAssessment } from '../../../core/models/ai-risk-assessment.model';
import { RiskAssessmentHistoryTableComponent } from './risk-assessment-history-table.component';

describe('RiskAssessmentHistoryTableComponent', () => {
  let fixture: ComponentFixture<RiskAssessmentHistoryTableComponent>;
  let component: RiskAssessmentHistoryTableComponent;
  let httpMock: HttpTestingController;
  const customerId = 'customer-1';
  const transactionId = 'txn-1';
  const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 };

  const assessment1: AiRiskAssessment = {
    assessmentId: 'a1',
    transactionId,
    triggeredAt: '2026-01-01T00:00:00Z',
    riskLevel: 'HIGH',
    riskScore: 80,
    findings: 'High value transaction',
    recommendations: 'Review manually',
    ruleContributions: [
      { ruleId: 'r1', ruleName: 'High-value transaction', scoreContribution: 20 },
    ],
  };
  const assessment2: AiRiskAssessment = { ...assessment1, assessmentId: 'a2' };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RiskAssessmentHistoryTableComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideNativeDateAdapter(),
      ],
    });
    fixture = TestBed.createComponent(RiskAssessmentHistoryTableComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('customerId', customerId);
    fixture.componentRef.setInput('transactionId', transactionId);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  function historyUrl(request: { url: string }): boolean {
    return request.url === `/api/v1/customers/${customerId}/ai-assessments`;
  }

  function flushInitial(page: object = emptyPage): void {
    httpMock.expectOne(historyUrl).flush(page);
    fixture.detectChanges();
  }

  function openFilterMenu(ariaLabel: string): void {
    const button = fixture.debugElement
      .queryAll(By.directive(MatMenuTrigger))
      .find((el) => (el.nativeElement as HTMLElement).getAttribute('aria-label') === ariaLabel);
    button?.injector.get(MatMenuTrigger).openMenu();
    fixture.detectChanges();
  }

  it('loads the history scoped to the transaction with default sort and pagination', () => {
    const req = httpMock.expectOne(historyUrl);
    expect(req.request.params.get('transactionId')).toBe(transactionId);
    expect(req.request.params.get('sort')).toBe('triggeredAt,desc');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    req.flush(emptyPage);
  });

  it('requests the new page and size on paginator change', () => {
    flushInitial();

    component.onPageChange({ pageIndex: 1, pageSize: 20 } as PageEvent);

    const req = httpMock.expectOne(historyUrl);
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('20');
    req.flush(emptyPage);
  });

  it('requests the new sort on sort change', () => {
    flushInitial();

    component.onSortChange({ active: 'riskScore', direction: 'asc' } as Sort);

    const req = httpMock.expectOne(historyUrl);
    expect(req.request.params.get('sort')).toBe('riskScore,asc');
    req.flush(emptyPage);
  });

  it('sends the risk level filter as a query param via the popover, debounced', fakeAsync(() => {
    flushInitial();

    openFilterMenu('Filter Risk Level');
    const select = document.querySelector('.mat-mdc-menu-panel mat-select') as HTMLElement;
    expect(select).toBeTruthy();

    component.onFilterChange('riskLevel', 'HIGH');
    tick(300);

    const req = httpMock.expectOne(historyUrl);
    expect(req.request.params.get('riskLevel')).toBe('HIGH');
    req.flush(emptyPage);
  }));

  it('sends min/max score filters as query params', fakeAsync(() => {
    flushInitial();

    component.onFilterChange('minScore', '10');
    tick(300);
    httpMock.expectOne(historyUrl).flush(emptyPage);

    component.onFilterChange('maxScore', '90');
    tick(300);

    const req = httpMock.expectOne(historyUrl);
    expect(req.request.params.get('minScore')).toBe('10');
    expect(req.request.params.get('maxScore')).toBe('90');
    req.flush(emptyPage);
  }));

  it('filters by a triggered-at date range, and clears it', fakeAsync(() => {
    flushInitial();

    component.onFromDateFilterChange(new Date(2026, 0, 1));
    tick(300);
    httpMock.expectOne(historyUrl).flush(emptyPage);

    component.onToDateFilterChange(new Date(2026, 0, 31));
    tick(300);
    const req = httpMock.expectOne(historyUrl);
    expect(req.request.params.get('from')).toBe(new Date(2026, 0, 1).toISOString());
    expect(req.request.params.get('to')).toBe(new Date(2026, 0, 31).toISOString());
    req.flush(emptyPage);

    component.clearDateFilter();
    tick(300);
    const clearedReq = httpMock.expectOne(historyUrl);
    expect(clearedReq.request.params.has('from')).toBeFalse();
    expect(clearedReq.request.params.has('to')).toBeFalse();
    clearedReq.flush(emptyPage);
  }));

  it('reload() resets to the first page and refetches', () => {
    flushInitial({
      content: [assessment1, assessment2],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 10,
    });
    component.onPageChange({ pageIndex: 1, pageSize: 10 } as PageEvent);
    httpMock.expectOne(historyUrl).flush(emptyPage);

    component.reload();

    expect(component.pageIndex()).toBe(0);
    httpMock.expectOne(historyUrl).flush(emptyPage);
  });

  it('resets filters and reloads when the transactionId input changes', fakeAsync(() => {
    flushInitial();

    component.onFilterChange('riskLevel', 'HIGH');
    tick(300);
    httpMock.expectOne(historyUrl).flush(emptyPage);

    fixture.componentRef.setInput('transactionId', 'txn-2');
    fixture.detectChanges();

    expect(component.filters()).toEqual({});
    const req = httpMock.expectOne(historyUrl);
    expect(req.request.params.get('transactionId')).toBe('txn-2');
    expect(req.request.params.has('riskLevel')).toBeFalse();
    req.flush(emptyPage);
  }));

  it('expands a row on click to reveal its fired rules, and collapses it on a second click', () => {
    flushInitial({
      content: [assessment1, assessment2],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 10,
    });

    expect(component.expandedAssessmentId()).toBeNull();
    let detailRows = fixture.debugElement.queryAll(By.css('tr.detail-row-open'));
    expect(detailRows.length).toBe(0);

    const assessmentRows = fixture.debugElement.queryAll(By.css('tr.assessment-row'));
    assessmentRows[0].nativeElement.click();
    fixture.detectChanges();

    expect(component.expandedAssessmentId()).toBe('a1');
    detailRows = fixture.debugElement.queryAll(By.css('tr.detail-row-open'));
    expect(detailRows.length).toBe(1);
    expect(detailRows[0].nativeElement.textContent).toContain('High-value transaction');
    expect(detailRows[0].nativeElement.textContent).toContain('+20');

    assessmentRows[0].nativeElement.click();
    fixture.detectChanges();

    expect(component.expandedAssessmentId()).toBeNull();
    expect(fixture.debugElement.queryAll(By.css('tr.detail-row-open')).length).toBe(0);
  });

  it('applies the shared header class and alternates row styling by index', () => {
    flushInitial({
      content: [assessment1, assessment2],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 10,
    });

    expect(fixture.debugElement.query(By.css('th.table-header-cell'))).toBeTruthy();

    const rows = fixture.debugElement.queryAll(By.css('tr.assessment-row'));
    expect(rows.length).toBe(2);
    expect((rows[0].nativeElement as HTMLElement).classList.contains('app-alt-row')).toBeFalse();
    expect((rows[1].nativeElement as HTMLElement).classList.contains('app-alt-row')).toBeTrue();
  });
});

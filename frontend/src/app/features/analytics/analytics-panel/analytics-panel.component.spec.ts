import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepicker } from '@angular/material/datepicker';
import { MatMenuTrigger } from '@angular/material/menu';
import { MatTooltip } from '@angular/material/tooltip';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { AnalyticsPanelComponent } from './analytics-panel.component';

describe('AnalyticsPanelComponent', () => {
  let fixture: ComponentFixture<AnalyticsPanelComponent>;
  let component: AnalyticsPanelComponent;
  let httpMock: HttpTestingController;
  const customerId = 'customer-1';
  const emptySeries = {
    activityType: null,
    granularity: 'DAY',
    from: '2026-01-01T00:00:00.000Z',
    to: '2026-01-31T00:00:00.000Z',
    buckets: [],
  };
  const rangeConstraints = {
    DAY: { minAmount: 1, minUnit: 'DAYS', maxAmount: 1, maxUnit: 'MONTHS' },
    WEEK: { minAmount: 1, minUnit: 'WEEKS', maxAmount: 30, maxUnit: 'WEEKS' },
    MONTH: { minAmount: 1, minUnit: 'MONTHS', maxAmount: 2, maxUnit: 'YEARS' },
    YEAR: { minAmount: 1, minUnit: 'YEARS', maxAmount: 5, maxUnit: 'YEARS' },
  };

  function analyticsUrl(request: { url: string }): boolean {
    return request.url === `/api/v1/customers/${customerId}/analytics`;
  }

  function constraintsUrl(request: { url: string }): boolean {
    return request.url === '/api/v1/analytics/range-constraints';
  }

  /**
   * Flushes the pending analytics request, echoing back whatever `from`/`to` params were actually
   * sent (mirroring the real backend, which uses an explicitly-provided side as-is) so a test that
   * picks a date doesn't have that pick silently clobbered by an unrelated fixed fixture value once
   * the response is synced back into `fromDate`/`toDate`.
   */
  function flushAnalyticsRequest(overrides: Partial<typeof emptySeries> = {}): void {
    const req = httpMock.expectOne(analyticsUrl);
    const from = req.request.params.get('from') ?? emptySeries.from;
    const to = req.request.params.get('to') ?? emptySeries.to;
    req.flush({ ...emptySeries, from, to, ...overrides });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AnalyticsPanelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideNativeDateAdapter(),
        provideCharts(withDefaultRegisterables()),
      ],
    });
    fixture = TestBed.createComponent(AnalyticsPanelComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(constraintsUrl).flush(rangeConstraints);
    fixture.componentRef.setInput('customerId', customerId);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads the default DAY-granularity series on init', () => {
    const req = httpMock.expectOne(analyticsUrl);
    expect(req.request.params.get('granularity')).toBe('DAY');
    req.flush(emptySeries);
  });

  it('requests the new granularity when changed, debounced', fakeAsync(() => {
    flushAnalyticsRequest();

    component.onGranularityChange('MONTH');
    tick(299);
    httpMock.expectNone(analyticsUrl);
    tick(1);

    const req = httpMock.expectOne(analyticsUrl);
    expect(req.request.params.get('granularity')).toBe('MONTH');
    req.flush(emptySeries);
  }));

  it('sends from/to as ISO strings when dates are picked', fakeAsync(() => {
    flushAnalyticsRequest();

    component.onFromDateChange(new Date('2026-01-01T00:00:00Z'));
    tick(300);
    flushAnalyticsRequest();

    component.onToDateChange(new Date('2026-02-01T00:00:00Z'));
    tick(300);

    const req = httpMock.expectOne(analyticsUrl);
    expect(req.request.params.get('from')).toBe('2026-01-01T00:00:00.000Z');
    expect(req.request.params.get('to')).toBe('2026-02-01T00:00:00.000Z');
    req.flush(emptySeries);
  }));

  it('resets type-specific filters and requeries when the activity type changes', fakeAsync(() => {
    flushAnalyticsRequest();

    component.onActivityTypeChange('CARD');
    tick(300);

    const req = httpMock.expectOne(analyticsUrl);
    expect(req.request.params.get('activityType')).toBe('CARD');
    expect(component.typeSpecificColumns().length).toBeGreaterThan(0);
    req.flush(emptySeries);
  }));

  it('switches the aggregation metric without issuing a new request', fakeAsync(() => {
    flushAnalyticsRequest();

    component.onMetricChange('amount');
    tick(300);

    httpMock.expectNone(analyticsUrl);
    expect(component.metric()).toBe('amount');
  }));

  it('renders the aggregation-type control as a dropdown, not a toggle group', () => {
    flushAnalyticsRequest();

    expect(fixture.debugElement.query(By.css('.metric-field mat-select'))).toBeTruthy();
    expect(fixture.debugElement.query(By.css('mat-button-toggle-group'))).toBeFalsy();
  });

  it('disables granularity options whose configured window does not fit the selected range', fakeAsync(() => {
    flushAnalyticsRequest();

    component.onFromDateChange(new Date(2026, 0, 1));
    tick(300);
    flushAnalyticsRequest();
    component.onToDateChange(new Date(2026, 0, 15)); // 14 days: fits DAY and WEEK, not MONTH/YEAR.
    tick(300);
    flushAnalyticsRequest();

    const available = component.availableGranularities();
    expect(available.has('DAY')).toBeTrue();
    expect(available.has('WEEK')).toBeTrue();
    expect(available.has('MONTH')).toBeFalse();
    expect(available.has('YEAR')).toBeFalse();
  }));

  it("computes the To datepicker's bounds from From plus the selected granularity", fakeAsync(() => {
    flushAnalyticsRequest();

    component.onFromDateChange(new Date(2026, 0, 15));
    tick(300);
    flushAnalyticsRequest();

    expect(component.toDatepickerMin()).toEqual(new Date(2026, 0, 16));
    expect(component.toDatepickerMax()).toEqual(new Date(2026, 1, 15));
  }));

  it('clears an already-picked To when a granularity change makes it invalid', fakeAsync(() => {
    flushAnalyticsRequest();

    component.onFromDateChange(new Date(2026, 0, 1));
    tick(300);
    flushAnalyticsRequest();
    component.onToDateChange(new Date(2026, 0, 3)); // 2 days: only fits DAY.
    tick(300);
    flushAnalyticsRequest();

    component.onGranularityChange('YEAR');
    tick(300);

    expect(component.toDate()).toBeNull();
    flushAnalyticsRequest();
  }));

  it('opens the filter menu and applies a secondary filter', fakeAsync(() => {
    flushAnalyticsRequest();

    const trigger = fixture.debugElement
      .queryAll(By.directive(MatMenuTrigger))
      .find(
        (el) => (el.nativeElement as HTMLElement).getAttribute('aria-label') === 'More filters',
      );
    trigger?.injector.get(MatMenuTrigger).openMenu();
    fixture.detectChanges();

    const input = document.querySelector('.filters-menu-content input') as HTMLInputElement;
    expect(input).toBeTruthy();
    input.value = 'EUR';
    input.dispatchEvent(new Event('input'));
    tick(300);

    const req = httpMock.expectOne(analyticsUrl);
    expect(req.request.params.get('currency')).toBe('EUR');
    req.flush(emptySeries);
  }));

  it('renders a structured inline error when the backend rejects the range', fakeAsync(() => {
    httpMock.expectOne(analyticsUrl).flush(
      {
        detail: 'Range [...] is not valid for granularity YEAR.',
        granularity: 'YEAR',
        minAmount: 1,
        minUnit: 'YEARS',
        maxAmount: 5,
        maxUnit: 'YEARS',
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    const error = component.errorDetail();
    expect(error?.granularity).toBe('YEAR');
    expect(error?.minAmount).toBe(1);
    expect(error?.maxAmount).toBe(5);
    expect(fixture.debugElement.query(By.css('.analytics-error'))).toBeTruthy();
  }));

  it('falls back to the plain detail string when the error has no structured extension properties', fakeAsync(() => {
    httpMock
      .expectOne(analyticsUrl)
      .flush({ detail: 'Something else went wrong' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(component.errorDetail()?.message).toBe('Something else went wrong');
    expect(component.errorDetail()?.granularity).toBeUndefined();
  }));

  it('renders the constraints tooltip as one aligned line per granularity', () => {
    flushAnalyticsRequest();

    const tooltip = component.constraintsTooltip();
    const lines = tooltip.split('\n');
    expect(lines).toHaveSize(4);
    expect(lines[0]).toContain('DAY:');
    expect(lines[1]).toContain('WEEK:');
    expect(lines[2]).toContain('MONTH:');
    expect(lines[3]).toContain('YEAR:');
  });

  it('has no active secondary filters by default', () => {
    flushAnalyticsRequest();

    expect(component.hasActiveSecondaryFilters()).toBeFalse();
    const trigger = fixture.debugElement
      .queryAll(By.directive(MatMenuTrigger))
      .find(
        (el) => (el.nativeElement as HTMLElement).getAttribute('aria-label') === 'More filters',
      );
    expect((trigger?.nativeElement as HTMLElement).classList.contains('filter-active')).toBeFalse();
  });

  it('marks the filter icon active when the activity type changes', fakeAsync(() => {
    flushAnalyticsRequest();

    component.onActivityTypeChange('CARD');
    tick(300);
    flushAnalyticsRequest();
    fixture.detectChanges();

    expect(component.hasActiveSecondaryFilters()).toBeTrue();
    const trigger = fixture.debugElement
      .queryAll(By.directive(MatMenuTrigger))
      .find(
        (el) => (el.nativeElement as HTMLElement).getAttribute('aria-label') === 'More filters',
      );
    expect((trigger?.nativeElement as HTMLElement).classList.contains('filter-active')).toBeTrue();
  }));

  it('marks the filter icon active when a secondary filter is set, and clears when reset', fakeAsync(() => {
    flushAnalyticsRequest();

    component.onFilterChange('currency', 'EUR');
    tick(300);
    flushAnalyticsRequest();

    expect(component.hasActiveSecondaryFilters()).toBeTrue();

    component.onFilterChange('currency', '');
    tick(300);
    flushAnalyticsRequest();

    expect(component.hasActiveSecondaryFilters()).toBeFalse();
  }));

  it('uses the server-computed To for the chart, but leaves the To picker blank once explicitly cleared', fakeAsync(() => {
    flushAnalyticsRequest(); // fromDate/toDate now both set from the fixture's echoed defaults

    // Clearing To alone leaves From at its already-synced value, reproducing a from-only request.
    component.onToDateChange(null);
    tick(300);
    const req = httpMock.expectOne(analyticsUrl);
    expect(req.request.params.get('from')).toBe(emptySeries.from);
    expect(req.request.params.has('to')).toBeFalse();
    req.flush({ ...emptySeries, to: '2026-02-15T00:00:00.000Z' });

    // The chart still renders using the server's computed range...
    expect(component.series()?.to).toBe('2026-02-15T00:00:00.000Z');
    // ...but the picker itself stays under the operator's control once explicitly cleared.
    expect(component.toDate()).toBeNull();
  }));

  it('stays blank across multiple subsequent reloads once explicitly cleared (e.g. while picking a granularity)', fakeAsync(() => {
    flushAnalyticsRequest();

    component.onToDateChange(null);
    tick(300);
    flushAnalyticsRequest();
    expect(component.toDate()).toBeNull();

    component.onGranularityChange('MONTH');
    tick(300);
    flushAnalyticsRequest();
    expect(component.toDate()).toBeNull();
  }));

  it("computes the From datepicker's bounds from To plus the selected granularity", fakeAsync(() => {
    flushAnalyticsRequest();

    component.onToDateChange(new Date(2026, 1, 15));
    tick(300);
    flushAnalyticsRequest();

    expect(component.fromDatepickerMin()).toEqual(new Date(2026, 0, 15));
    expect(component.fromDatepickerMax()).toEqual(new Date(2026, 1, 14));
  }));

  it('caps the From datepicker max at least a min-span before today when neither side is picked yet', () => {
    const today = new Date();
    expect(component.fromDatepickerMax() < today).toBeTrue();
    flushAnalyticsRequest();
  });

  it('caps the To datepicker max at today, never allowing a future date', fakeAsync(() => {
    flushAnalyticsRequest();

    const before = Date.now();
    component.onFromDateChange(new Date());
    tick(300);
    flushAnalyticsRequest();
    const after = Date.now();

    // The uncapped max (from + 1 month) would be far outside this bracket; only a same-moment
    // "today" cap lands inside it.
    const max = component.toDatepickerMax().getTime();
    expect(max).toBeGreaterThanOrEqual(before);
    expect(max).toBeLessThanOrEqual(after);
  }));

  it('shows a clear icon per populated date field, and clicking it drops that request param', fakeAsync(() => {
    flushAnalyticsRequest();
    fixture.detectChanges();

    const clearButtons = fixture.debugElement.queryAll(By.css('.clear-date-button'));
    expect(clearButtons.length).toBeGreaterThan(0);

    clearButtons[0].nativeElement.click();
    tick(300);

    const req = httpMock.expectOne(analyticsUrl);
    expect(req.request.params.has('from')).toBeFalse();
    req.flush(emptySeries);
  }));

  it('shows the constraints tooltip on the Granularity label instead of a separate icon', () => {
    flushAnalyticsRequest();
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.info-trigger'))).toBeFalsy();
    const tooltipDebugEl = fixture.debugElement.query(By.directive(MatTooltip));
    expect(tooltipDebugEl).toBeTruthy();
    const tooltip = tooltipDebugEl.injector.get(MatTooltip);
    expect(tooltip.message).toBe(component.constraintsTooltip());
  });

  it("points the To calendar's startAt at the boundary that maximizes the window", fakeAsync(() => {
    flushAnalyticsRequest();

    component.onFromDateChange(new Date(2026, 0, 15));
    tick(300);
    flushAnalyticsRequest();
    fixture.detectChanges();

    const pickers = fixture.debugElement.queryAll(By.directive(MatDatepicker));
    const toPicker = pickers[1].injector.get(MatDatepicker);
    expect(toPicker.startAt).toEqual(component.toDatepickerMax());
  }));

  it("points the From calendar's startAt at the boundary that maximizes the window", fakeAsync(() => {
    flushAnalyticsRequest();

    component.onToDateChange(new Date(2026, 1, 15));
    tick(300);
    flushAnalyticsRequest();
    fixture.detectChanges();

    const pickers = fixture.debugElement.queryAll(By.directive(MatDatepicker));
    const fromPicker = pickers[0].injector.get(MatDatepicker);
    expect(fromPicker.startAt).toEqual(component.fromDatepickerMin());
  }));

  it('resets the date pickers and touched-state when switching customers', fakeAsync(() => {
    flushAnalyticsRequest();
    component.onFromDateChange(new Date(2026, 0, 15));
    tick(300);
    flushAnalyticsRequest();
    expect(component.fromDate()).toEqual(new Date(2026, 0, 15));

    fixture.componentRef.setInput('customerId', 'customer-2');
    fixture.detectChanges();

    // Immediately after the switch, before the new load resolves, the pickers are blank again.
    expect(component.fromDate()).toBeNull();
    expect(component.toDate()).toBeNull();

    // The new load's response is now free to sync both sides again (touched-state was reset).
    const req = httpMock.expectOne(
      (r: { url: string }) => r.url === '/api/v1/customers/customer-2/analytics',
    );
    req.flush(emptySeries);
    expect(component.fromDate()).toEqual(new Date(emptySeries.from));
    expect(component.toDate()).toEqual(new Date(emptySeries.to));
  }));
});

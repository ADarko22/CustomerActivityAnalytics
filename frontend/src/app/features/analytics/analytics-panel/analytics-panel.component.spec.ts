import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { AnalyticsPanelComponent } from './analytics-panel.component';

describe('AnalyticsPanelComponent', () => {
  let fixture: ComponentFixture<AnalyticsPanelComponent>;
  let component: AnalyticsPanelComponent;
  let httpMock: HttpTestingController;
  const customerId = 'customer-1';
  const emptySeries = { activityType: null, granularity: 'DAY', from: '', to: '', buckets: [] };

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
    fixture.componentRef.setInput('customerId', customerId);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  function analyticsUrl(request: { url: string }): boolean {
    return request.url === `/api/v1/customers/${customerId}/analytics`;
  }

  it('loads the default DAY-granularity series on init', () => {
    const req = httpMock.expectOne(analyticsUrl);
    expect(req.request.params.get('granularity')).toBe('DAY');
    req.flush(emptySeries);
  });

  it('requests the new granularity when changed, debounced', fakeAsync(() => {
    httpMock.expectOne(analyticsUrl).flush(emptySeries);

    component.onGranularityChange('MONTH');
    tick(299);
    httpMock.expectNone(analyticsUrl);
    tick(1);

    const req = httpMock.expectOne(analyticsUrl);
    expect(req.request.params.get('granularity')).toBe('MONTH');
    req.flush(emptySeries);
  }));

  it('sends from/to as ISO strings when dates are picked', fakeAsync(() => {
    httpMock.expectOne(analyticsUrl).flush(emptySeries);

    component.onFromDateChange(new Date('2026-01-01T00:00:00Z'));
    tick(300);
    httpMock.expectOne(analyticsUrl).flush(emptySeries);

    component.onToDateChange(new Date('2026-02-01T00:00:00Z'));
    tick(300);

    const req = httpMock.expectOne(analyticsUrl);
    expect(req.request.params.get('from')).toBe('2026-01-01T00:00:00.000Z');
    expect(req.request.params.get('to')).toBe('2026-02-01T00:00:00.000Z');
    req.flush(emptySeries);
  }));

  it('resets type-specific filters and requeries when the activity type changes', fakeAsync(() => {
    httpMock.expectOne(analyticsUrl).flush(emptySeries);

    component.onActivityTypeChange('CARD');
    tick(300);

    const req = httpMock.expectOne(analyticsUrl);
    expect(req.request.params.get('activityType')).toBe('CARD');
    expect(component.typeSpecificColumns().length).toBeGreaterThan(0);
    req.flush(emptySeries);
  }));

  it('switches the aggregation metric without issuing a new request', fakeAsync(() => {
    httpMock.expectOne(analyticsUrl).flush(emptySeries);

    component.onMetricChange('amount');
    tick(300);

    httpMock.expectNone(analyticsUrl);
    expect(component.metric()).toBe('amount');
  }));

  it('shows the backend error message on an invalid range/granularity response', fakeAsync(() => {
    httpMock
      .expectOne(analyticsUrl)
      .flush(
        { detail: 'Range is not valid for granularity YEAR' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Range is not valid for granularity YEAR');
  }));
});

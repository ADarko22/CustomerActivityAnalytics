import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AnalyticsService } from './analytics.service';

describe('AnalyticsService', () => {
  let service: AnalyticsService;
  let httpMock: HttpTestingController;
  const customerId = 'customer-1';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AnalyticsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('builds the granularity param', () => {
    service.findTimeSeries(customerId, {}, 'MONTH').subscribe();

    const req = httpMock.expectOne((r) => r.url === `/api/v1/customers/${customerId}/analytics`);
    expect(req.request.params.get('granularity')).toBe('MONTH');
    req.flush({ activityType: null, granularity: 'MONTH', from: '', to: '', buckets: [] });
  });

  it('includes only defined filter params', () => {
    service
      .findTimeSeries(customerId, { activityType: 'CARD', currency: 'EUR' }, 'DAY')
      .subscribe();

    const req = httpMock.expectOne((r) => r.url === `/api/v1/customers/${customerId}/analytics`);
    expect(req.request.params.get('activityType')).toBe('CARD');
    expect(req.request.params.get('currency')).toBe('EUR');
    expect(req.request.params.has('status')).toBeFalse();
    req.flush({ activityType: 'CARD', granularity: 'DAY', from: '', to: '', buckets: [] });
  });
});

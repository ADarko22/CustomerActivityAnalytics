import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AnalyticsConfigService } from './analytics-config.service';

describe('AnalyticsConfigService', () => {
  let service: AnalyticsConfigService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AnalyticsConfigService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('fetches the active range constraints', () => {
    service.getRangeConstraints().subscribe();

    const req = httpMock.expectOne('/api/v1/analytics/range-constraints');
    expect(req.request.method).toBe('GET');
    req.flush({
      DAY: { minAmount: 1, minUnit: 'DAYS', maxAmount: 1, maxUnit: 'MONTHS' },
      WEEK: { minAmount: 1, minUnit: 'WEEKS', maxAmount: 30, maxUnit: 'WEEKS' },
      MONTH: { minAmount: 1, minUnit: 'MONTHS', maxAmount: 2, maxUnit: 'YEARS' },
      YEAR: { minAmount: 1, minUnit: 'YEARS', maxAmount: 5, maxUnit: 'YEARS' },
    });
  });
});

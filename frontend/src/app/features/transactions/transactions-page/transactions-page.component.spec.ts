import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { routes } from '../../../app.routes';
import { AuthService } from '../../../core/services/auth.service';

describe('TransactionsPageComponent (routing)', () => {
  let harness: RouterTestingHarness;
  let httpMock: HttpTestingController;
  const customerId = 'customer-1';
  const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
  const emptySeries = {
    activityType: null,
    granularity: 'DAY',
    from: '2026-01-01T00:00:00.000Z',
    to: '2026-01-31T00:00:00.000Z',
    buckets: [],
  };

  beforeEach(async () => {
    const authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['isLoggedIn', 'isAdmin']);
    authServiceSpy.isLoggedIn.and.returnValue(true);
    authServiceSpy.isAdmin.and.returnValue(false);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideNativeDateAdapter(),
        provideCharts(withDefaultRegisterables()),
        provideRouter(routes, withComponentInputBinding()),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    httpMock.verify();
  });

  function transactionsUrl(request: { url: string }): boolean {
    return request.url === `/api/v1/customers/${customerId}/transactions`;
  }

  function analyticsUrl(request: { url: string }): boolean {
    return request.url === `/api/v1/customers/${customerId}/analytics`;
  }

  function constraintsUrl(request: { url: string }): boolean {
    return request.url === '/api/v1/analytics/range-constraints';
  }

  const rangeConstraints = {
    DAY: { minAmount: 1, minUnit: 'DAYS', maxAmount: 1, maxUnit: 'MONTHS' },
    WEEK: { minAmount: 1, minUnit: 'WEEKS', maxAmount: 30, maxUnit: 'WEEKS' },
    MONTH: { minAmount: 1, minUnit: 'MONTHS', maxAmount: 2, maxUnit: 'YEARS' },
    YEAR: { minAmount: 1, minUnit: 'YEARS', maxAmount: 5, maxUnit: 'YEARS' },
  };

  it('activates the transaction table on the transactions route', async () => {
    await harness.navigateByUrl(`/customers/${customerId}/transactions`);

    expect(TestBed.inject(Router).url).toBe(`/customers/${customerId}/transactions`);
    httpMock.expectOne(transactionsUrl).flush(emptyPage);
  });

  it('activates the analytics panel on the analytics route', async () => {
    await harness.navigateByUrl(`/customers/${customerId}/analytics`);

    expect(TestBed.inject(Router).url).toBe(`/customers/${customerId}/analytics`);
    httpMock.expectOne(constraintsUrl).flush(rangeConstraints);
    httpMock.expectOne(analyticsUrl).flush(emptySeries);
  });

  it('redirects the bare customer route to the transactions tab', async () => {
    await harness.navigateByUrl(`/customers/${customerId}`);

    expect(TestBed.inject(Router).url).toBe(`/customers/${customerId}/transactions`);
    httpMock.expectOne(transactionsUrl).flush(emptyPage);
  });

  it('renders tab links pointing at the transactions and analytics routes', async () => {
    await harness.navigateByUrl(`/customers/${customerId}/transactions`);
    httpMock.expectOne(transactionsUrl).flush(emptyPage);

    const links = harness.fixture.debugElement
      .queryAll(By.css('a[mat-tab-link]'))
      .map((link) => (link.nativeElement as HTMLAnchorElement).getAttribute('href'));

    expect(links).toEqual([
      `/customers/${customerId}/transactions`,
      `/customers/${customerId}/analytics`,
    ]);
  });

  it('navigates to the analytics URL when the Analytics tab link is clicked', async () => {
    await harness.navigateByUrl(`/customers/${customerId}/transactions`);
    httpMock.expectOne(transactionsUrl).flush(emptyPage);

    const analyticsLink = harness.fixture.debugElement.queryAll(By.css('a[mat-tab-link]'))[1]
      .nativeElement as HTMLAnchorElement;
    analyticsLink.dispatchEvent(
      new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }),
    );
    await harness.fixture.whenStable();
    harness.fixture.detectChanges();

    expect(TestBed.inject(Router).url).toBe(`/customers/${customerId}/analytics`);
    httpMock.expectOne(constraintsUrl).flush(rangeConstraints);
    httpMock.expectOne(analyticsUrl).flush(emptySeries);
  });
});

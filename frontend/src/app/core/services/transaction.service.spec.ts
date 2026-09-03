import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TransactionService } from './transaction.service';

describe('TransactionService', () => {
  let service: TransactionService;
  let httpMock: HttpTestingController;
  const customerId = 'customer-1';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TransactionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('builds pagination and sort params', () => {
    service.findOverview(customerId, {}, 1, 20, 'createdAt,desc').subscribe();

    const req = httpMock.expectOne((r) => r.url === `/api/v1/customers/${customerId}/transactions`);
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('sort')).toBe('createdAt,desc');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 1, size: 20 });
  });

  it('includes only defined filter params', () => {
    service
      .findOverview(customerId, { activityType: 'CARD', merchantName: 'Amazon' }, 0, 20)
      .subscribe();

    const req = httpMock.expectOne((r) => r.url === `/api/v1/customers/${customerId}/transactions`);
    expect(req.request.params.get('activityType')).toBe('CARD');
    expect(req.request.params.get('merchantName')).toBe('Amazon');
    expect(req.request.params.has('status')).toBeFalse();
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
  });

  it('requests transaction detail by id', () => {
    service.findDetail(customerId, 'txn-1').subscribe();

    const req = httpMock.expectOne(`/api/v1/customers/${customerId}/transactions/txn-1`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CustomerService } from './customer.service';

describe('CustomerService', () => {
  let service: CustomerService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CustomerService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('requests the given query and pagination', () => {
    service.search('ang', 0, 5).subscribe();

    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/customers' &&
        r.params.get('query') === 'ang' &&
        r.params.get('size') === '5',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 5 });
  });

  it('defaults to size 5 when omitted', () => {
    service.search('').subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/customers');
    expect(req.request.params.get('size')).toBe('5');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 5 });
  });
});

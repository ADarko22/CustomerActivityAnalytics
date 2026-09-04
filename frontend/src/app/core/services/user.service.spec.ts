import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UserService } from './user.service';

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('fetches the current operator profile', () => {
    service.getCurrentUser().subscribe();

    const req = httpMock.expectOne('/api/v1/me');
    expect(req.request.method).toBe('GET');
    req.flush({
      username: 'operator',
      firstName: 'Olivia',
      lastName: 'Operator',
      email: 'operator@example.com',
      roles: ['OPERATOR'],
    });
  });
});

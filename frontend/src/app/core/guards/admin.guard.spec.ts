import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { adminGuard } from './admin.guard';

describe('adminGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['isAdmin']);

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: authServiceSpy }],
    });
    router = TestBed.inject(Router);
  });

  it('allows activation for an admin', () => {
    authServiceSpy.isAdmin.and.returnValue(true);

    const result = TestBed.runInInjectionContext(() =>
      adminGuard({} as never, { url: '/administration' } as never),
    );

    expect(result).toBe(true);
  });

  it('redirects a non-admin to the root route', () => {
    authServiceSpy.isAdmin.and.returnValue(false);

    const result = TestBed.runInInjectionContext(() =>
      adminGuard({} as never, { url: '/administration' } as never),
    );

    expect(result).toEqual(router.createUrlTree(['']));
    expect(result instanceof UrlTree).toBe(true);
  });
});

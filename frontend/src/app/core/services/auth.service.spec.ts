import { TestBed } from '@angular/core/testing';
import { OAuthService } from 'angular-oauth2-oidc';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let oauthServiceSpy: jasmine.SpyObj<OAuthService>;

  beforeEach(() => {
    oauthServiceSpy = jasmine.createSpyObj<OAuthService>('OAuthService', [
      'hasValidAccessToken',
      'getIdentityClaims',
      'logOut',
    ]);

    TestBed.configureTestingModule({
      providers: [{ provide: OAuthService, useValue: oauthServiceSpy }],
    });
    service = TestBed.inject(AuthService);
  });

  it('reflects OAuthService.hasValidAccessToken for isLoggedIn', () => {
    oauthServiceSpy.hasValidAccessToken.and.returnValue(true);
    expect(service.isLoggedIn()).toBe(true);

    oauthServiceSpy.hasValidAccessToken.and.returnValue(false);
    expect(service.isLoggedIn()).toBe(false);
  });

  it('extracts realm roles from the identity claims', () => {
    oauthServiceSpy.getIdentityClaims.and.returnValue({
      realm_access: { roles: ['OPERATOR', 'ADMIN'] },
    });
    expect(service.getRoles()).toEqual(['OPERATOR', 'ADMIN']);
  });

  it('returns an empty role list when identity claims are absent', () => {
    oauthServiceSpy.getIdentityClaims.and.returnValue(
      undefined as unknown as Record<string, unknown>,
    );
    expect(service.getRoles()).toEqual([]);
  });

  it('isAdmin is true only when ADMIN is among the roles', () => {
    oauthServiceSpy.getIdentityClaims.and.returnValue({ realm_access: { roles: ['ADMIN'] } });
    expect(service.isAdmin()).toBe(true);

    oauthServiceSpy.getIdentityClaims.and.returnValue({ realm_access: { roles: ['OPERATOR'] } });
    expect(service.isAdmin()).toBe(false);
  });

  it('logout delegates to OAuthService.logOut', () => {
    service.logout();
    expect(oauthServiceSpy.logOut).toHaveBeenCalled();
  });
});

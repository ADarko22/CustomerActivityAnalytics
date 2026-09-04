import { Injectable, inject } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauthService = inject(OAuthService);

  isLoggedIn(): boolean {
    return this.oauthService.hasValidAccessToken();
  }

  getRoles(): string[] {
    const claims = this.oauthService.getIdentityClaims() as
      { realm_access?: { roles?: string[] } } | undefined;
    return claims?.realm_access?.roles ?? [];
  }

  isAdmin(): boolean {
    return this.getRoles().includes('ADMIN');
  }

  logout(): void {
    this.oauthService.logOut();
  }
}

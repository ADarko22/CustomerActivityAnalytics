import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Denies activation outright (no `UrlTree` redirect) when not logged in. The `''` route is
 * itself guarded by this same guard, so redirecting there on failure creates a route that
 * immediately re-denies and re-redirects to itself — an infinite client-side navigation loop
 * that pegs the CPU and freezes the tab before the app-initializer's pending full-page redirect
 * to Keycloak (which resolves this in practice, see `app.config.ts`) ever gets a chance to run.
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);

  return authService.isLoggedIn();
};

import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, inject, signal } from '@angular/core';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faRightFromBracket } from '@fortawesome/free-solid-svg-icons';
import { filter } from 'rxjs';
import { CustomerSearchComponent } from './features/customer-search/customer-search.component';
import { AuthService } from './core/services/auth.service';
import { UserService } from './core/services/user.service';
import { UserProfile } from './core/models/user-profile.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    FaIconComponent,
    CustomerSearchComponent,
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private readonly authService = inject(AuthService);
  private readonly userService = inject(UserService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly title = 'Customer Activity Analytics';
  readonly currentUser = signal<UserProfile | null>(null);
  readonly logoutIcon = faRightFromBracket;
  readonly isAdministrationRoute = signal(false);
  readonly routeCustomerId = signal<string | undefined>(undefined);

  constructor() {
    this.userService.getCurrentUser().subscribe((user) => this.currentUser.set(user));

    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => {
        this.isAdministrationRoute.set(event.urlAfterRedirects.startsWith('/administration'));
        this.routeCustomerId.set(this.deepestCustomerId());
      });
  }

  private deepestCustomerId(): string | undefined {
    let route = this.route.root;
    while (route.firstChild) {
      route = route.firstChild;
    }
    return route.snapshot.paramMap.get('customerId') ?? undefined;
  }

  isAdmin(): boolean {
    return this.authService.isAdmin();
  }

  logout(): void {
    this.authService.logout();
  }
}

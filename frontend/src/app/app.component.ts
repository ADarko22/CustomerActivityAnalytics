import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faRightFromBracket } from '@fortawesome/free-solid-svg-icons';
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

  readonly title = 'Customer Activity Analytics';
  readonly currentUser = signal<UserProfile | null>(null);
  readonly logoutIcon = faRightFromBracket;

  constructor() {
    this.userService.getCurrentUser().subscribe((user) => this.currentUser.set(user));
  }

  isAdmin(): boolean {
    return this.authService.isAdmin();
  }

  logout(): void {
    this.authService.logout();
  }
}

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { AuthService } from './core/services/auth.service';

describe('AppComponent', () => {
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['isAdmin', 'logout']);
    authServiceSpy.isAdmin.and.returnValue(false);

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  function flushCurrentUser(): void {
    httpMock.expectOne('/api/v1/me').flush({
      username: 'operator',
      firstName: 'Olivia',
      lastName: 'Operator',
      email: 'operator@example.com',
      roles: ['OPERATOR'],
    });
  }

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
    flushCurrentUser();

    httpMock
      .expectOne((r) => r.url === '/api/v1/customers')
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 5 });
  });

  it('should render the title', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Customer Activity Analytics');

    httpMock
      .expectOne((r) => r.url === '/api/v1/customers')
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 5 });
    flushCurrentUser();
  });

  it('renders the logged-in user name once /me resolves', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/v1/customers')
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 5 });
    flushCurrentUser();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.app-user-name')?.textContent).toContain('Olivia Operator');
  });

  it('hides the Administration nav link for a non-admin', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/v1/customers')
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 5 });
    flushCurrentUser();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).not.toContain('Administration');
  });

  it('shows the Administration nav link for an admin', () => {
    authServiceSpy.isAdmin.and.returnValue(true);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/v1/customers')
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 5 });
    flushCurrentUser();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Administration');
  });

  it('logout button invokes AuthService.logout', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/v1/customers')
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 5 });
    flushCurrentUser();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const button = compiled.querySelector('button');
    button?.dispatchEvent(new Event('click'));

    expect(authServiceSpy.logout).toHaveBeenCalled();
  });
});

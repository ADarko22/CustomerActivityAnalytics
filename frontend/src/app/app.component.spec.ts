import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { AuthService } from './core/services/auth.service';

@Component({ selector: 'app-route-stub', standalone: true, template: '' })
class RouteStubComponent {}

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
        provideRouter([
          { path: 'administration', component: RouteStubComponent },
          { path: 'customers/:customerId/transactions', component: RouteStubComponent },
        ]),
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

  function flushCustomerSuggestions(): void {
    httpMock
      .expectOne((r) => r.url === '/api/v1/customers')
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 5 });
  }

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
    fixture.detectChanges();
    flushCurrentUser();
    flushCustomerSuggestions();
  });

  it('should render the title', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Customer Activity Analytics');

    flushCustomerSuggestions();
    flushCurrentUser();
  });

  it('renders the logged-in user name once /me resolves', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    flushCustomerSuggestions();
    flushCurrentUser();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.app-user-name')?.textContent).toContain('Olivia Operator');
  });

  it('hides the Administration nav link for a non-admin', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    flushCustomerSuggestions();
    flushCurrentUser();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).not.toContain('Administration');
  });

  it('shows the Administration nav link for an admin', () => {
    authServiceSpy.isAdmin.and.returnValue(true);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    flushCustomerSuggestions();
    flushCurrentUser();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Administration');
  });

  it('logout button invokes AuthService.logout', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    flushCustomerSuggestions();
    flushCurrentUser();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const button = compiled.querySelector('button');
    button?.dispatchEvent(new Event('click'));

    expect(authServiceSpy.logout).toHaveBeenCalled();
  });

  it('hides the customer search box on the Administration route', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    flushCustomerSuggestions();
    flushCurrentUser();

    await TestBed.inject(Router).navigateByUrl('/administration');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-customer-search')).toBeNull();
  });

  it('shows the customer search box outside the Administration route', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    flushCustomerSuggestions();
    flushCurrentUser();

    await TestBed.inject(Router).navigateByUrl('/customers/abc-123/transactions');
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/v1/customers/abc-123')
      .flush({ customerId: 'abc-123', firstName: 'Angelo', lastName: 'Buono' });

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-customer-search')).not.toBeNull();
  });

  it('passes the deep-linked customerId down to the search box', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    flushCustomerSuggestions();
    flushCurrentUser();

    await TestBed.inject(Router).navigateByUrl('/customers/abc-123/transactions');
    fixture.detectChanges();

    expect(fixture.componentInstance.routeCustomerId()).toBe('abc-123');
    httpMock
      .expectOne((r) => r.url === '/api/v1/customers/abc-123')
      .flush({ customerId: 'abc-123', firstName: 'Angelo', lastName: 'Buono' });
  });
});

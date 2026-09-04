import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { RiskRule } from '../../../core/models/risk-rule.model';
import { AuthService } from '../../../core/services/auth.service';
import { RiskRulesTableComponent } from './risk-rules-table.component';

describe('RiskRulesTableComponent', () => {
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const rule: RiskRule = {
    ruleId: 'rule-1',
    ruleName: 'High-value transaction',
    appliesTo: 'ALL',
    thresholdLogic: 'amount > 5000',
    weight: 30,
  };

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['isAdmin']);
    authServiceSpy.isAdmin.and.returnValue(false);

    await TestBed.configureTestingModule({
      imports: [RiskRulesTableComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimationsAsync(),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads rules on init', () => {
    const fixture = TestBed.createComponent(RiskRulesTableComponent);
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/risk-rules');
    req.flush({ content: [rule], totalElements: 1, totalPages: 1, number: 0, size: 20 });

    expect(fixture.componentInstance.rules()).toEqual([rule]);
  });

  it('reloads with the appliesTo filter set', () => {
    const fixture = TestBed.createComponent(RiskRulesTableComponent);
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/v1/risk-rules')
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });

    fixture.componentInstance.onFilterChange('CARD');

    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/risk-rules' && r.params.get('appliesTo') === 'CARD',
    );
    expect(req.request.params.get('appliesTo')).toBe('CARD');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
  });

  it('reload() refetches the current page', () => {
    const fixture = TestBed.createComponent(RiskRulesTableComponent);
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/v1/risk-rules')
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });

    fixture.componentInstance.reload();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/risk-rules');
    req.flush({ content: [rule], totalElements: 1, totalPages: 1, number: 0, size: 20 });
    expect(fixture.componentInstance.rules()).toEqual([rule]);
  });

  it('hides admin actions for a non-admin', () => {
    const fixture = TestBed.createComponent(RiskRulesTableComponent);
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/v1/risk-rules')
      .flush({ content: [rule], totalElements: 1, totalPages: 1, number: 0, size: 20 });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('button[mat-icon-button]').length).toBe(0);
  });

  it('shows admin actions for an admin', () => {
    authServiceSpy.isAdmin.and.returnValue(true);
    const fixture = TestBed.createComponent(RiskRulesTableComponent);
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/v1/risk-rules')
      .flush({ content: [rule], totalElements: 1, totalPages: 1, number: 0, size: 20 });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('button[mat-icon-button]').length).toBe(2);
  });
});

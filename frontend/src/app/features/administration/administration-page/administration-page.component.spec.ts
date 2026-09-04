import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of } from 'rxjs';
import { RiskRule } from '../../../core/models/risk-rule.model';
import { AuthService } from '../../../core/services/auth.service';
import { AdministrationPageComponent } from './administration-page.component';

describe('AdministrationPageComponent', () => {
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  const rule: RiskRule = {
    ruleId: 'rule-1',
    ruleName: 'High-value transaction',
    appliesTo: 'ALL',
    thresholdLogic: 'amount > 5000',
    weight: 30,
  };

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['isAdmin']);
    authServiceSpy.isAdmin.and.returnValue(true);
    dialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [AdministrationPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimationsAsync(),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: MatDialog, useValue: dialogSpy },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialLoad(): void {
    httpMock
      .expectOne((r) => r.url === '/api/v1/risk-rules')
      .flush({ content: [rule], totalElements: 1, totalPages: 1, number: 0, size: 20 });
  }

  it('shows the Add Rule button for an admin', () => {
    const fixture = TestBed.createComponent(AdministrationPageComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Add Rule');
  });

  it('hides the Add Rule button for a non-admin', () => {
    authServiceSpy.isAdmin.and.returnValue(false);
    const fixture = TestBed.createComponent(AdministrationPageComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).not.toContain('Add Rule');
  });

  it('creates a rule and reloads the table after the dialog closes with a value', () => {
    dialogSpy.open.and.returnValue({
      afterClosed: () =>
        of({ ruleName: 'New rule', appliesTo: 'ALL', thresholdLogic: 'logic', weight: 15 }),
    } as never);

    const fixture = TestBed.createComponent(AdministrationPageComponent);
    fixture.detectChanges();
    flushInitialLoad();

    fixture.componentInstance.openCreate();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/risk-rules' && r.method === 'POST');
    expect(req.request.body).toEqual({
      ruleName: 'New rule',
      appliesTo: 'ALL',
      thresholdLogic: 'logic',
      weight: 15,
    });
    req.flush(rule);
    flushInitialLoad();
  });

  it('does nothing when the create dialog closes without a value', () => {
    dialogSpy.open.and.returnValue({ afterClosed: () => of(undefined) } as never);

    const fixture = TestBed.createComponent(AdministrationPageComponent);
    fixture.detectChanges();
    flushInitialLoad();

    fixture.componentInstance.openCreate();

    expect(() => httpMock.expectNone((r) => r.method === 'POST')).not.toThrow();
  });

  it('deletes a rule after confirmation and reloads the table', () => {
    const confirmSpy = spyOn(window, 'confirm').and.returnValue(true);

    const fixture = TestBed.createComponent(AdministrationPageComponent);
    fixture.detectChanges();
    flushInitialLoad();

    fixture.componentInstance.onDeleteRequested(rule);

    expect(confirmSpy).toHaveBeenCalledWith('Delete risk rule "High-value transaction"?');
    httpMock
      .expectOne((r) => r.url === '/api/v1/risk-rules/rule-1' && r.method === 'DELETE')
      .flush(null);
    flushInitialLoad();
  });

  it('does not delete when the confirmation is declined', () => {
    spyOn(window, 'confirm').and.returnValue(false);

    const fixture = TestBed.createComponent(AdministrationPageComponent);
    fixture.detectChanges();
    flushInitialLoad();

    fixture.componentInstance.onDeleteRequested(rule);

    expect(() => httpMock.expectNone((r) => r.method === 'DELETE')).not.toThrow();
  });
});

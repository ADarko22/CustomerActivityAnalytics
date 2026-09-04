import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { RiskRule } from '../../../core/models/risk-rule.model';
import { AuthService } from '../../../core/services/auth.service';
import { RiskRulesTableComponent } from './risk-rules-table.component';

describe('RiskRulesTableComponent', () => {
  let fixture: ComponentFixture<RiskRulesTableComponent>;
  let component: RiskRulesTableComponent;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };

  const rule: RiskRule = {
    ruleId: 'rule-1',
    ruleName: 'High-value transaction',
    appliesTo: 'ALL',
    thresholdLogic: 'amount > 5000',
    weight: 30,
  };

  function riskRulesUrl(request: { url: string }): boolean {
    return request.url === '/api/v1/risk-rules';
  }

  function flushInitial(page: object = emptyPage): void {
    httpMock.expectOne(riskRulesUrl).flush(page);
  }

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
    fixture = TestBed.createComponent(RiskRulesTableComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads rules on init with the default sort', () => {
    const req = httpMock.expectOne(riskRulesUrl);
    expect(req.request.params.get('sort')).toBe('ruleName,asc');
    req.flush({ content: [rule], totalElements: 1, totalPages: 1, number: 0, size: 20 });

    expect(component.rules()).toEqual([rule]);
  });

  it('requests the new page and size on paginator change', () => {
    flushInitial();

    component.onPageChange({ pageIndex: 1, pageSize: 50 } as PageEvent);

    const req = httpMock.expectOne(riskRulesUrl);
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('50');
    req.flush(emptyPage);
  });

  it('requests the new sort on sort change', () => {
    flushInitial();

    component.onSortChange({ active: 'weight', direction: 'desc' } as Sort);

    const req = httpMock.expectOne(riskRulesUrl);
    expect(req.request.params.get('sort')).toBe('weight,desc');
    req.flush(emptyPage);
  });

  it('sends the appliesTo filter as a query param, debounced', fakeAsync(() => {
    flushInitial();

    component.onFilterChange('appliesTo', 'CARD');
    tick(300);

    const req = httpMock.expectOne(riskRulesUrl);
    expect(req.request.params.get('appliesTo')).toBe('CARD');
    req.flush(emptyPage);
  }));

  it('sends the ruleName text filter as a query param, debounced', fakeAsync(() => {
    flushInitial();

    component.onFilterChange('ruleName', 'high-value');
    tick(300);

    const req = httpMock.expectOne(riskRulesUrl);
    expect(req.request.params.get('ruleName')).toBe('high-value');
    req.flush(emptyPage);
  }));

  it('sends min/max weight filters as query params', fakeAsync(() => {
    flushInitial();

    component.onFilterChange('minWeight', '10');
    tick(300);
    httpMock.expectOne(riskRulesUrl).flush(emptyPage);

    component.onFilterChange('maxWeight', '40');
    tick(300);

    const req = httpMock.expectOne(riskRulesUrl);
    expect(req.request.params.get('minWeight')).toBe('10');
    expect(req.request.params.get('maxWeight')).toBe('40');
    req.flush(emptyPage);
  }));

  it('clearFilter clears the param and the native input', fakeAsync(() => {
    flushInitial();
    const textInput = document.createElement('input');
    textInput.value = 'high-value';

    component.clearFilter('ruleName', textInput);
    tick(300);

    expect(textInput.value).toBe('');
    const req = httpMock.expectOne(riskRulesUrl);
    expect(req.request.params.has('ruleName')).toBeFalse();
    req.flush(emptyPage);
  }));

  it('filterValue reflects the currently active filter for a column', fakeAsync(() => {
    flushInitial();

    component.onFilterChange('appliesTo', 'CARD');

    expect(component.filterValue('appliesTo')).toBe('CARD');
    tick(300);
    httpMock.expectOne(riskRulesUrl).flush(emptyPage);
  }));

  it('clearWeightFilter clears both weight params and the native inputs', fakeAsync(() => {
    flushInitial();
    const minInput = document.createElement('input');
    const maxInput = document.createElement('input');
    minInput.value = '10';
    maxInput.value = '40';

    component.clearWeightFilter(minInput, maxInput);
    tick(300);

    expect(minInput.value).toBe('');
    expect(maxInput.value).toBe('');
    const req = httpMock.expectOne(riskRulesUrl);
    expect(req.request.params.has('minWeight')).toBeFalse();
    expect(req.request.params.has('maxWeight')).toBeFalse();
    req.flush(emptyPage);
  }));

  it('reload() refetches the current page', () => {
    flushInitial();

    component.reload();

    const req = httpMock.expectOne(riskRulesUrl);
    req.flush({ content: [rule], totalElements: 1, totalPages: 1, number: 0, size: 20 });
    expect(component.rules()).toEqual([rule]);
  });

  it('hides admin edit/delete actions for a non-admin', () => {
    httpMock
      .expectOne(riskRulesUrl)
      .flush({ content: [rule], totalElements: 1, totalPages: 1, number: 0, size: 20 });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector(`[aria-label="Edit ${rule.ruleName}"]`)).toBeNull();
    expect(compiled.querySelector(`[aria-label="Delete ${rule.ruleName}"]`)).toBeNull();
  });

  it('shows admin edit/delete actions for an admin', () => {
    authServiceSpy.isAdmin.and.returnValue(true);
    httpMock
      .expectOne(riskRulesUrl)
      .flush({ content: [rule], totalElements: 1, totalPages: 1, number: 0, size: 20 });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector(`[aria-label="Edit ${rule.ruleName}"]`)).not.toBeNull();
    expect(compiled.querySelector(`[aria-label="Delete ${rule.ruleName}"]`)).not.toBeNull();
  });
});

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RiskRule, RiskRuleWrite } from '../models/risk-rule.model';
import { RiskRuleService } from './risk-rule.service';

describe('RiskRuleService', () => {
  let service: RiskRuleService;
  let httpMock: HttpTestingController;

  const rule: RiskRule = {
    ruleId: 'rule-1',
    ruleName: 'High-value transaction',
    appliesTo: 'ALL',
    thresholdLogic: 'amount > 5000',
    weight: 30,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RiskRuleService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists rules with pagination and an optional appliesTo filter', () => {
    service.list('CARD', 0, 20, 'ruleName,asc').subscribe();

    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/risk-rules' &&
        r.params.get('appliesTo') === 'CARD' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '20' &&
        r.params.get('sort') === 'ruleName,asc',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [rule], totalElements: 1, totalPages: 1, number: 0, size: 20 });
  });

  it('omits the appliesTo param when not provided', () => {
    service.list(undefined, 0, 20).subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/risk-rules');
    expect(req.request.params.has('appliesTo')).toBe(false);
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
  });

  it('creates a new rule', () => {
    const dto: RiskRuleWrite = {
      ruleName: 'New rule',
      appliesTo: 'ALL',
      thresholdLogic: 'logic',
      weight: 15,
    };
    service.create(dto).subscribe();

    const req = httpMock.expectOne('/api/v1/risk-rules');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dto);
    req.flush(rule);
  });

  it('updates an existing rule', () => {
    const dto: RiskRuleWrite = {
      ruleName: 'Updated',
      appliesTo: 'PAYMENT',
      thresholdLogic: 'new logic',
      weight: 40,
    };
    service.update('rule-1', dto).subscribe();

    const req = httpMock.expectOne('/api/v1/risk-rules/rule-1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(dto);
    req.flush(rule);
  });

  it('deletes a rule', () => {
    service.delete('rule-1').subscribe();

    const req = httpMock.expectOne('/api/v1/risk-rules/rule-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});

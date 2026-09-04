import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../models/page.model';
import { ActivityScope, RiskRule, RiskRuleWrite } from '../models/risk-rule.model';

@Injectable({ providedIn: 'root' })
export class RiskRuleService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/risk-rules';

  list(
    appliesTo: ActivityScope | undefined,
    page: number,
    size: number,
    sort?: string,
  ): Observable<Page<RiskRule>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (sort) {
      params = params.set('sort', sort);
    }
    if (appliesTo) {
      params = params.set('appliesTo', appliesTo);
    }
    return this.http.get<Page<RiskRule>>(this.baseUrl, { params });
  }

  create(dto: RiskRuleWrite): Observable<RiskRule> {
    return this.http.post<RiskRule>(this.baseUrl, dto);
  }

  update(ruleId: string, dto: RiskRuleWrite): Observable<RiskRule> {
    return this.http.put<RiskRule>(`${this.baseUrl}/${ruleId}`, dto);
  }

  delete(ruleId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${ruleId}`);
  }
}

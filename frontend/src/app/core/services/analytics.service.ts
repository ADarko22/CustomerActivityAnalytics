import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AnalyticsTimeSeries, Granularity } from '../models/analytics.model';
import { TransactionFilter } from '../models/transaction.model';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);

  findTimeSeries(
    customerId: string,
    filter: TransactionFilter,
    granularity: Granularity,
  ): Observable<AnalyticsTimeSeries> {
    let params = new HttpParams().set('granularity', granularity);
    for (const [key, value] of Object.entries(filter)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<AnalyticsTimeSeries>(`/api/v1/customers/${customerId}/analytics`, {
      params,
    });
  }
}

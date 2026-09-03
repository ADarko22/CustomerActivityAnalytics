import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../models/page.model';
import { Transaction, TransactionFilter } from '../models/transaction.model';

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);

  findOverview(
    customerId: string,
    filter: TransactionFilter,
    page: number,
    size: number,
    sort?: string,
  ): Observable<Page<Transaction>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (sort) {
      params = params.set('sort', sort);
    }
    for (const [key, value] of Object.entries(filter)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<Transaction>>(`/api/v1/customers/${customerId}/transactions`, {
      params,
    });
  }

  findDetail(customerId: string, transactionId: string): Observable<Transaction> {
    return this.http.get<Transaction>(
      `/api/v1/customers/${customerId}/transactions/${transactionId}`,
    );
  }
}

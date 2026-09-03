import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RangeConstraints } from '../models/analytics.model';

@Injectable({ providedIn: 'root' })
export class AnalyticsConfigService {
  private readonly http = inject(HttpClient);

  getRangeConstraints(): Observable<RangeConstraints> {
    return this.http.get<RangeConstraints>('/api/v1/analytics/range-constraints');
  }
}

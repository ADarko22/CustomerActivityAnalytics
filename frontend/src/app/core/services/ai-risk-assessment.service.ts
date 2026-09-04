import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../models/page.model';
import {
  AiRiskAssessment,
  AiRiskAssessmentEvent,
  AiRiskAssessmentFilter,
} from '../models/ai-risk-assessment.model';

/** Injectable so tests can substitute a fake implementing onmessage/onerror/close. */
export type EventSourceFactory = (url: string) => EventSource;

const defaultEventSourceFactory: EventSourceFactory = (url) => new EventSource(url);

@Injectable({ providedIn: 'root' })
export class AiRiskAssessmentService {
  private readonly http = inject(HttpClient);

  streamAssessment(
    customerId: string,
    transactionId: string,
    eventSourceFactory: EventSourceFactory = defaultEventSourceFactory,
  ): Observable<AiRiskAssessmentEvent> {
    const url = `/api/v1/customers/${customerId}/ai-assessments/stream?transactionId=${transactionId}`;
    return new Observable<AiRiskAssessmentEvent>((subscriber) => {
      const eventSource = eventSourceFactory(url);

      eventSource.onmessage = (event: MessageEvent) => {
        const parsed = JSON.parse(event.data) as AiRiskAssessmentEvent;
        subscriber.next(parsed);
        // The server ends the HTTP response normally on COMPLETE/FAILED; without this, the
        // browser's default EventSource reconnect behavior would re-trigger a brand new
        // assessment run against the same URL.
        if (parsed.stage === 'COMPLETE' || parsed.stage === 'FAILED') {
          subscriber.complete();
        }
      };

      eventSource.onerror = (event: Event) => {
        subscriber.error(event);
      };

      return () => eventSource.close();
    });
  }

  findHistory(
    customerId: string,
    transactionId: string,
    filter: AiRiskAssessmentFilter,
    page: number,
    size: number,
    sort?: string,
  ): Observable<Page<AiRiskAssessment>> {
    let params = new HttpParams()
      .set('transactionId', transactionId)
      .set('page', page)
      .set('size', size);
    if (sort) {
      params = params.set('sort', sort);
    }
    for (const [key, value] of Object.entries(filter)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<AiRiskAssessment>>(`/api/v1/customers/${customerId}/ai-assessments`, {
      params,
    });
  }
}

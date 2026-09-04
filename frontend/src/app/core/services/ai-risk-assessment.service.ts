import {
  HttpClient,
  HttpDownloadProgressEvent,
  HttpEventType,
  HttpParams,
  HttpResponse,
} from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../models/page.model';
import {
  AiRiskAssessment,
  AiRiskAssessmentEvent,
  AiRiskAssessmentFilter,
} from '../models/ai-risk-assessment.model';

const LOG_PREFIX = '[AiRiskAssessmentService]';

@Injectable({ providedIn: 'root' })
export class AiRiskAssessmentService {
  private readonly http = inject(HttpClient);

  /**
   * Streams progress over HttpClient (not the native EventSource API) so the request goes
   * through the same DefaultOAuthInterceptor as every other call and carries the Bearer token —
   * EventSource has no header-injection hook, so it would otherwise be rejected with a 401 before
   * ever reaching the backend. Angular's fetch-backed HttpClient (`withFetch()`, app.config.ts)
   * reports incremental chunks as DownloadProgress events with a cumulative `partialText`, which
   * is parsed here as SSE framing (`data: {...}` lines separated by a blank line).
   */
  streamAssessment(customerId: string, transactionId: string): Observable<AiRiskAssessmentEvent> {
    const url = `/api/v1/customers/${customerId}/ai-assessments/stream?transactionId=${transactionId}`;
    const context = `customerId=${customerId}, transactionId=${transactionId}`;

    return new Observable<AiRiskAssessmentEvent>((subscriber) => {
      let processedUpTo = 0;

      console.debug(`${LOG_PREFIX} stream opening: ${context}`);

      const subscription = this.http
        .get(url, { reportProgress: true, observe: 'events', responseType: 'text' })
        .subscribe({
          next: (httpEvent) => {
            let text: string;
            if (httpEvent.type === HttpEventType.DownloadProgress) {
              text = (httpEvent as HttpDownloadProgressEvent).partialText ?? '';
            } else if (httpEvent.type === HttpEventType.Response) {
              text = (httpEvent as HttpResponse<string>).body ?? '';
            } else {
              return;
            }

            let boundary: number;
            while ((boundary = text.indexOf('\n\n', processedUpTo)) !== -1) {
              const rawEvent = text.slice(processedUpTo, boundary);
              processedUpTo = boundary + 2;

              const dataLine = rawEvent.split('\n').find((line) => line.startsWith('data:'));
              if (!dataLine) {
                continue;
              }
              const parsed = JSON.parse(
                dataLine.slice('data:'.length).trim(),
              ) as AiRiskAssessmentEvent;
              subscriber.next(parsed);
              // The server ends the HTTP response normally on COMPLETE/FAILED; without this, the
              // subscription would keep waiting on a response that has already finished.
              if (parsed.stage === 'COMPLETE' || parsed.stage === 'FAILED') {
                console.debug(`${LOG_PREFIX} stream finished: ${context}, stage=${parsed.stage}`);
                subscriber.complete();
                return;
              }
            }
          },
          error: (err) => {
            console.error(`${LOG_PREFIX} stream failed: ${context}`, err);
            subscriber.error(err);
          },
        });

      return () => subscription.unsubscribe();
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

import { HttpDownloadProgressEvent, HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AiRiskAssessmentEvent } from '../models/ai-risk-assessment.model';
import { AiRiskAssessmentService } from './ai-risk-assessment.service';

function sseChunk(event: AiRiskAssessmentEvent): string {
  return `data: ${JSON.stringify(event)}\n\n`;
}

function progressEvent(partialText: string): HttpDownloadProgressEvent {
  return { type: HttpEventType.DownloadProgress, loaded: partialText.length, partialText };
}

describe('AiRiskAssessmentService', () => {
  let service: AiRiskAssessmentService;
  let httpMock: HttpTestingController;
  const customerId = 'customer-1';
  const transactionId = 'txn-1';
  const streamUrl = `/api/v1/customers/${customerId}/ai-assessments/stream?transactionId=${transactionId}`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AiRiskAssessmentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('emits parsed progress events without closing the connection', () => {
    const received: AiRiskAssessmentEvent[] = [];
    let completed = false;

    service.streamAssessment(customerId, transactionId).subscribe({
      next: (event) => received.push(event),
      complete: () => (completed = true),
    });

    const req = httpMock.expectOne(streamUrl);
    let cumulative = sseChunk({ stage: 'PROMPT_BUILDING' });
    req.event(progressEvent(cumulative));
    cumulative += sseChunk({ stage: 'RULE_RETRIEVAL' });
    req.event(progressEvent(cumulative));

    expect(received.map((e) => e.stage)).toEqual(['PROMPT_BUILDING', 'RULE_RETRIEVAL']);
    expect(completed).toBeFalse();

    req.flush(cumulative);
  });

  it('buffers an event split across two chunks until the blank-line delimiter arrives', () => {
    const received: AiRiskAssessmentEvent[] = [];

    service.streamAssessment(customerId, transactionId).subscribe({
      next: (event) => received.push(event),
    });

    const req = httpMock.expectOne(streamUrl);
    const full = sseChunk({ stage: 'PROMPT_BUILDING' });
    const splitPoint = Math.floor(full.length / 2);

    req.event(progressEvent(full.slice(0, splitPoint)));
    expect(received).toEqual([]);

    req.event(progressEvent(full));
    expect(received.map((e) => e.stage)).toEqual(['PROMPT_BUILDING']);

    req.flush(full);
  });

  it('completes and cancels the request on COMPLETE', () => {
    let completed = false;

    service
      .streamAssessment(customerId, transactionId)
      .subscribe({ complete: () => (completed = true) });

    const req = httpMock.expectOne(streamUrl);
    req.event(
      progressEvent(
        sseChunk({
          stage: 'COMPLETE',
          result: {
            assessmentId: 'a1',
            transactionId,
            triggeredAt: '2026-01-01T00:00:00Z',
            riskLevel: 'LOW',
            riskScore: 10,
            findings: 'f',
            recommendations: 'r',
            ruleContributions: [],
          },
        }),
      ),
    );

    expect(completed).toBeTrue();
    expect(req.cancelled).toBeTrue();
  });

  it('completes and cancels the request on FAILED', () => {
    let completed = false;

    service
      .streamAssessment(customerId, transactionId)
      .subscribe({ complete: () => (completed = true) });

    const req = httpMock.expectOne(streamUrl);
    req.event(progressEvent(sseChunk({ stage: 'FAILED', message: 'boom' })));

    expect(completed).toBeTrue();
    expect(req.cancelled).toBeTrue();
  });

  it('propagates connection errors (e.g. a 401 from a missing auth token)', () => {
    let erroredOut = false;

    service
      .streamAssessment(customerId, transactionId)
      .subscribe({ error: () => (erroredOut = true) });

    const req = httpMock.expectOne(streamUrl);
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(erroredOut).toBeTrue();
  });

  it('builds history query params including filters, pagination and sort', () => {
    service
      .findHistory(
        customerId,
        transactionId,
        { riskLevel: 'HIGH', minScore: 10, maxScore: 90 },
        0,
        10,
        'triggeredAt,desc',
      )
      .subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `/api/v1/customers/${customerId}/ai-assessments`,
    );
    expect(req.request.params.get('transactionId')).toBe(transactionId);
    expect(req.request.params.get('riskLevel')).toBe('HIGH');
    expect(req.request.params.get('minScore')).toBe('10');
    expect(req.request.params.get('maxScore')).toBe('90');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('triggeredAt,desc');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
  });
});

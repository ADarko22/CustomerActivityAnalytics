import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AiRiskAssessmentEvent } from '../models/ai-risk-assessment.model';
import { AiRiskAssessmentService } from './ai-risk-assessment.service';

class FakeEventSource {
  onmessage: ((event: MessageEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  closed = false;

  constructor(public url: string) {}

  close(): void {
    this.closed = true;
  }

  emit(data: AiRiskAssessmentEvent): void {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(data) }));
  }

  emitError(): void {
    this.onerror?.(new Event('error'));
  }
}

describe('AiRiskAssessmentService', () => {
  let service: AiRiskAssessmentService;
  let httpMock: HttpTestingController;
  const customerId = 'customer-1';
  const transactionId = 'txn-1';

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
    let fake!: FakeEventSource;
    const received: AiRiskAssessmentEvent[] = [];
    let completed = false;

    service
      .streamAssessment(
        customerId,
        transactionId,
        (url) => (fake = new FakeEventSource(url)) as unknown as EventSource,
      )
      .subscribe({
        next: (event) => received.push(event),
        complete: () => (completed = true),
      });

    expect(fake.url).toBe(
      `/api/v1/customers/${customerId}/ai-assessments/stream?transactionId=${transactionId}`,
    );
    fake.emit({ stage: 'PROMPT_BUILDING' });
    fake.emit({ stage: 'RULE_RETRIEVAL' });

    expect(received.map((e) => e.stage)).toEqual(['PROMPT_BUILDING', 'RULE_RETRIEVAL']);
    expect(completed).toBeFalse();
    expect(fake.closed).toBeFalse();
  });

  it('completes and closes the EventSource on COMPLETE', () => {
    let fake!: FakeEventSource;
    let completed = false;

    service
      .streamAssessment(
        customerId,
        transactionId,
        (url) => (fake = new FakeEventSource(url)) as unknown as EventSource,
      )
      .subscribe({ complete: () => (completed = true) });

    fake.emit({
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
    });

    expect(completed).toBeTrue();
    expect(fake.closed).toBeTrue();
  });

  it('completes and closes the EventSource on FAILED', () => {
    let fake!: FakeEventSource;
    let completed = false;

    service
      .streamAssessment(
        customerId,
        transactionId,
        (url) => (fake = new FakeEventSource(url)) as unknown as EventSource,
      )
      .subscribe({ complete: () => (completed = true) });

    fake.emit({ stage: 'FAILED', message: 'boom' });

    expect(completed).toBeTrue();
    expect(fake.closed).toBeTrue();
  });

  it('propagates connection errors and closes the EventSource', () => {
    let fake!: FakeEventSource;
    let erroredOut = false;

    service
      .streamAssessment(
        customerId,
        transactionId,
        (url) => (fake = new FakeEventSource(url)) as unknown as EventSource,
      )
      .subscribe({ error: () => (erroredOut = true) });

    fake.emitError();

    expect(erroredOut).toBeTrue();
    expect(fake.closed).toBeTrue();
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

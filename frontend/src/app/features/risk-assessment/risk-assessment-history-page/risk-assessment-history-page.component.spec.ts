import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { RiskAssessmentHistoryPageComponent } from './risk-assessment-history-page.component';

describe('RiskAssessmentHistoryPageComponent', () => {
  let fixture: ComponentFixture<RiskAssessmentHistoryPageComponent>;
  let httpMock: HttpTestingController;
  const customerId = 'customer-1';
  const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RiskAssessmentHistoryPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideNativeDateAdapter(),
      ],
    });
    fixture = TestBed.createComponent(RiskAssessmentHistoryPageComponent);
    fixture.componentRef.setInput('customerId', customerId);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('renders the history table scoped to the customer, with no transactionId param', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(
      (r) => r.url === `/api/v1/customers/${customerId}/ai-assessments`,
    );
    expect(req.request.params.has('transactionId')).toBeFalse();
    req.flush(emptyPage);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('AI Risk Assessments');
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { Subject } from 'rxjs';
import { AiRiskAssessmentEvent } from '../../../core/models/ai-risk-assessment.model';
import { AiRiskAssessmentService } from '../../../core/services/ai-risk-assessment.service';
import { RiskAssessmentTriggerComponent } from './risk-assessment-trigger.component';

describe('RiskAssessmentTriggerComponent', () => {
  let fixture: ComponentFixture<RiskAssessmentTriggerComponent>;
  let component: RiskAssessmentTriggerComponent;
  let events$: Subject<AiRiskAssessmentEvent>;
  let streamAssessmentSpy: jasmine.Spy;

  beforeEach(() => {
    events$ = new Subject<AiRiskAssessmentEvent>();
    const serviceStub = {
      streamAssessment: () => events$.asObservable(),
    };
    streamAssessmentSpy = spyOn(serviceStub, 'streamAssessment').and.callThrough();

    TestBed.configureTestingModule({
      imports: [RiskAssessmentTriggerComponent],
      providers: [
        provideNoopAnimations(),
        { provide: AiRiskAssessmentService, useValue: serviceStub },
      ],
    });
    fixture = TestBed.createComponent(RiskAssessmentTriggerComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('customerId', 'customer-1');
    fixture.componentRef.setInput('transactionId', 'txn-1');
    fixture.detectChanges();
  });

  it('shows the trigger button in the idle state', () => {
    expect(fixture.debugElement.query(By.css('button'))?.nativeElement.textContent).toContain(
      'Run AI Risk Assessment',
    );
  });

  it('renders each stage as it arrives, marking earlier stages done and the latest current', () => {
    fixture.debugElement.query(By.css('button')).nativeElement.click();
    expect(streamAssessmentSpy).toHaveBeenCalledWith('customer-1', 'txn-1');

    events$.next({ stage: 'PROMPT_BUILDING' });
    fixture.detectChanges();
    expect(component.isStageCurrent('PROMPT_BUILDING')).toBeTrue();
    expect(component.isStageDone('PROMPT_BUILDING')).toBeFalse();

    events$.next({ stage: 'RULE_RETRIEVAL' });
    fixture.detectChanges();
    expect(component.isStageDone('PROMPT_BUILDING')).toBeTrue();
    expect(component.isStageCurrent('RULE_RETRIEVAL')).toBeTrue();
  });

  it('replaces the progress list with the final result card on COMPLETE', () => {
    fixture.debugElement.query(By.css('button')).nativeElement.click();
    events$.next({ stage: 'PROMPT_BUILDING' });
    events$.next({ stage: 'MODEL_CALL' });

    let addedEmitted = false;
    component.assessmentAdded.subscribe(() => (addedEmitted = true));

    events$.next({
      stage: 'COMPLETE',
      result: {
        assessmentId: 'a1',
        transactionId: 'txn-1',
        triggeredAt: '2026-01-01T00:00:00Z',
        riskLevel: 'HIGH',
        riskScore: 85,
        findings: 'Elevated risk detected',
        recommendations: 'Escalate for manual review',
        ruleContributions: [],
      },
    });
    fixture.detectChanges();

    expect(component.viewState()).toBe('complete');
    expect(addedEmitted).toBeTrue();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('HIGH');
    expect(text).toContain('Elevated risk detected');
    expect(fixture.debugElement.query(By.css('.stage-list'))).toBeFalsy();
  });

  it('shows an inline error card with a Retry button on FAILED, without an assessmentAdded emission', () => {
    fixture.debugElement.query(By.css('button')).nativeElement.click();
    let addedEmitted = false;
    component.assessmentAdded.subscribe(() => (addedEmitted = true));

    events$.next({ stage: 'FAILED', message: 'Assessment could not be completed. Please retry.' });
    fixture.detectChanges();

    expect(component.viewState()).toBe('failed');
    expect(addedEmitted).toBeFalse();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Assessment could not be completed');
    expect(text).toContain('Retry');
  });

  it('shows the error card when the SSE connection itself errors out', () => {
    fixture.debugElement.query(By.css('button')).nativeElement.click();
    events$.error(new Event('error'));
    fixture.detectChanges();

    expect(component.viewState()).toBe('failed');
  });
});

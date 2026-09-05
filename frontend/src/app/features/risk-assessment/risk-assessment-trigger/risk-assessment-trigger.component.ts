import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleCheck, faShieldHalved } from '@fortawesome/free-solid-svg-icons';
import {
  AiRiskAssessment,
  AiRiskAssessmentEvent,
  AssessmentStage,
} from '../../../core/models/ai-risk-assessment.model';
import { AiRiskAssessmentService } from '../../../core/services/ai-risk-assessment.service';
import { RiskLevelBadgeComponent } from '../risk-level-badge/risk-level-badge.component';
import { RuleContributionsListComponent } from '../rule-contributions-list/rule-contributions-list.component';

type ViewState = 'idle' | 'running' | 'complete' | 'failed';

const PROGRESS_STAGES: AssessmentStage[] = [
  'PROMPT_BUILDING',
  'RULE_RETRIEVAL',
  'HISTORY_RETRIEVAL',
  'GUARDRAIL_CHECK',
  'MODEL_CALL',
];

const STAGE_LABELS: Record<AssessmentStage, string> = {
  PROMPT_BUILDING: 'Building prompt',
  RULE_RETRIEVAL: 'Retrieving risk rules',
  HISTORY_RETRIEVAL: 'Retrieving assessment history',
  GUARDRAIL_CHECK: 'Running safety checks',
  MODEL_CALL: 'Calling AI model',
  COMPLETE: 'Complete',
  FAILED: 'Failed',
};

const DEFAULT_ERROR_MESSAGE = 'Connection lost while assessing this transaction.';

@Component({
  selector: 'app-risk-assessment-trigger',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    FaIconComponent,
    RiskLevelBadgeComponent,
    RuleContributionsListComponent,
  ],
  templateUrl: './risk-assessment-trigger.component.html',
  styleUrl: './risk-assessment-trigger.component.scss',
})
export class RiskAssessmentTriggerComponent {
  @Input({ required: true }) customerId!: string;
  @Input({ required: true }) transactionId!: string;
  @Output() readonly assessmentAdded = new EventEmitter<void>();

  private readonly aiRiskAssessmentService = inject(AiRiskAssessmentService);

  readonly faShieldHalved = faShieldHalved;
  readonly faCircleCheck = faCircleCheck;
  readonly stageOrder = PROGRESS_STAGES;
  readonly stageLabels = STAGE_LABELS;

  readonly viewState = signal<ViewState>('idle');
  readonly seenStages = signal<AssessmentStage[]>([]);
  readonly result = signal<AiRiskAssessment | null>(null);
  readonly errorMessage = signal<string | null>(null);

  trigger(): void {
    this.viewState.set('running');
    this.seenStages.set([]);
    this.result.set(null);
    this.errorMessage.set(null);

    this.aiRiskAssessmentService.streamAssessment(this.customerId, this.transactionId).subscribe({
      next: (event) => this.onEvent(event),
      error: () => {
        this.errorMessage.set(DEFAULT_ERROR_MESSAGE);
        this.viewState.set('failed');
      },
    });
  }

  isStageDone(stage: AssessmentStage): boolean {
    const stages = this.seenStages();
    const index = stages.indexOf(stage);
    return index !== -1 && index < stages.length - 1;
  }

  isStageCurrent(stage: AssessmentStage): boolean {
    const stages = this.seenStages();
    return stages.length > 0 && stages[stages.length - 1] === stage;
  }

  private onEvent(event: AiRiskAssessmentEvent): void {
    if (event.stage === 'COMPLETE') {
      this.result.set(event.result ?? null);
      this.viewState.set('complete');
      this.assessmentAdded.emit();
      return;
    }
    if (event.stage === 'FAILED') {
      this.errorMessage.set(event.message ?? DEFAULT_ERROR_MESSAGE);
      this.viewState.set('failed');
      return;
    }
    this.seenStages.update((stages) => [...stages, event.stage]);
  }
}

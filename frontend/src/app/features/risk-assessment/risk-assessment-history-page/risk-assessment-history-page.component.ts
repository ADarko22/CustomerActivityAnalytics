import { Component, Input } from '@angular/core';
import { RiskAssessmentHistoryTableComponent } from '../risk-assessment-history-table/risk-assessment-history-table.component';

@Component({
  selector: 'app-risk-assessment-history-page',
  standalone: true,
  imports: [RiskAssessmentHistoryTableComponent],
  templateUrl: './risk-assessment-history-page.component.html',
  styleUrl: './risk-assessment-history-page.component.scss',
})
export class RiskAssessmentHistoryPageComponent {
  @Input({ required: true }) customerId!: string;
}

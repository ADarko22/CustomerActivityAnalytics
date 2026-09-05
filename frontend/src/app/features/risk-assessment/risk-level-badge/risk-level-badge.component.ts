import { Component, Input } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { RiskLevel } from '../../../core/models/ai-risk-assessment.model';

@Component({
  selector: 'app-risk-level-badge',
  standalone: true,
  imports: [MatChipsModule],
  templateUrl: './risk-level-badge.component.html',
  styleUrl: './risk-level-badge.component.scss',
})
export class RiskLevelBadgeComponent {
  @Input({ required: true }) level!: RiskLevel;
}

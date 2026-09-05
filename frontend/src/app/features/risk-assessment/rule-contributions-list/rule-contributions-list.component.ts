import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { RuleContribution } from '../../../core/models/ai-risk-assessment.model';

@Component({
  selector: 'app-rule-contributions-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './rule-contributions-list.component.html',
  styleUrl: './rule-contributions-list.component.scss',
})
export class RuleContributionsListComponent {
  @Input({ required: true }) contributions!: RuleContribution[];

  get sortedContributions(): RuleContribution[] {
    return [...this.contributions].sort((a, b) => b.scoreContribution - a.scoreContribution);
  }
}

import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { RiskAssessmentHistoryTableComponent } from '../risk-assessment-history-table/risk-assessment-history-table.component';

export interface RiskAssessmentHistoryDialogData {
  customerId: string;
  transactionId: string;
}

@Component({
  selector: 'app-risk-assessment-history-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, FaIconComponent, RiskAssessmentHistoryTableComponent],
  templateUrl: './risk-assessment-history-dialog.component.html',
  styleUrl: './risk-assessment-history-dialog.component.scss',
})
export class RiskAssessmentHistoryDialogComponent {
  readonly data = inject<RiskAssessmentHistoryDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<RiskAssessmentHistoryDialogComponent>);

  readonly faXmark = faXmark;

  close(): void {
    this.dialogRef.close();
  }
}

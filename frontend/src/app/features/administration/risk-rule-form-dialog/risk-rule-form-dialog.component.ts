import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivityScope, RiskRule, RiskRuleWrite } from '../../../core/models/risk-rule.model';

export interface RiskRuleFormDialogData {
  rule: RiskRule | null;
}

@Component({
  selector: 'app-risk-rule-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
  ],
  templateUrl: './risk-rule-form-dialog.component.html',
  styleUrl: './risk-rule-form-dialog.component.scss',
})
export class RiskRuleFormDialogComponent {
  readonly data = inject<RiskRuleFormDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<RiskRuleFormDialogComponent, RiskRuleWrite>);

  readonly scopeOptions: ActivityScope[] = ['CARD', 'PAYMENT', 'CRYPTO', 'ALL'];
  readonly isEdit = this.data.rule !== null;

  readonly form = new FormGroup({
    ruleName: new FormControl(this.data.rule?.ruleName ?? '', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    appliesTo: new FormControl<ActivityScope>(this.data.rule?.appliesTo ?? 'ALL', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    thresholdLogic: new FormControl(this.data.rule?.thresholdLogic ?? '', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    weight: new FormControl(this.data.rule?.weight ?? 0, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0)],
    }),
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.dialogRef.close(this.form.getRawValue());
  }

  cancel(): void {
    this.dialogRef.close();
  }
}

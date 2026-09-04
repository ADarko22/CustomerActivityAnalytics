import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { RiskRule } from '../../../core/models/risk-rule.model';
import {
  RiskRuleFormDialogComponent,
  RiskRuleFormDialogData,
} from './risk-rule-form-dialog.component';

describe('RiskRuleFormDialogComponent', () => {
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<RiskRuleFormDialogComponent>>;

  const existingRule: RiskRule = {
    ruleId: 'rule-1',
    ruleName: 'High-value transaction',
    appliesTo: 'CARD',
    thresholdLogic: 'amount > 5000',
    weight: 30,
  };

  function configure(data: RiskRuleFormDialogData): void {
    dialogRefSpy = jasmine.createSpyObj<MatDialogRef<RiskRuleFormDialogComponent>>('MatDialogRef', [
      'close',
    ]);

    TestBed.configureTestingModule({
      imports: [RiskRuleFormDialogComponent],
      providers: [
        provideAnimationsAsync(),
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
  }

  it('starts blank for create mode', () => {
    configure({ rule: null });
    const fixture = TestBed.createComponent(RiskRuleFormDialogComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.isEdit).toBe(false);
    expect(fixture.componentInstance.form.value.ruleName).toBe('');
  });

  it('pre-fills the form for edit mode', () => {
    configure({ rule: existingRule });
    const fixture = TestBed.createComponent(RiskRuleFormDialogComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.isEdit).toBe(true);
    expect(fixture.componentInstance.form.value.ruleName).toBe('High-value transaction');
    expect(fixture.componentInstance.form.value.appliesTo).toBe('CARD');
  });

  it('does not close on save when the form is invalid', () => {
    configure({ rule: null });
    const fixture = TestBed.createComponent(RiskRuleFormDialogComponent);
    fixture.detectChanges();

    fixture.componentInstance.save();

    expect(dialogRefSpy.close).not.toHaveBeenCalled();
    expect(fixture.componentInstance.form.touched).toBe(true);
  });

  it('closes with the form value on a valid save', () => {
    configure({ rule: null });
    const fixture = TestBed.createComponent(RiskRuleFormDialogComponent);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({
      ruleName: 'New rule',
      appliesTo: 'PAYMENT',
      thresholdLogic: 'logic',
      weight: 15,
    });
    fixture.componentInstance.save();

    expect(dialogRefSpy.close).toHaveBeenCalledWith({
      ruleName: 'New rule',
      appliesTo: 'PAYMENT',
      thresholdLogic: 'logic',
      weight: 15,
    });
  });

  it('cancel closes with no value', () => {
    configure({ rule: null });
    const fixture = TestBed.createComponent(RiskRuleFormDialogComponent);
    fixture.detectChanges();

    fixture.componentInstance.cancel();

    expect(dialogRefSpy.close).toHaveBeenCalledWith();
  });
});

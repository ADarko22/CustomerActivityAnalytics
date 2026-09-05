import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { RiskLevel } from '../../../core/models/ai-risk-assessment.model';
import { RiskLevelBadgeComponent } from './risk-level-badge.component';

describe('RiskLevelBadgeComponent', () => {
  let fixture: ComponentFixture<RiskLevelBadgeComponent>;

  const setLevel = (level: RiskLevel) => {
    TestBed.configureTestingModule({ imports: [RiskLevelBadgeComponent] });
    fixture = TestBed.createComponent(RiskLevelBadgeComponent);
    fixture.componentRef.setInput('level', level);
    fixture.detectChanges();
  };

  it('renders LOW with the risk-chip-low class', () => {
    setLevel('LOW');
    const chip = fixture.debugElement.query(By.css('mat-chip'));
    expect(chip.nativeElement.classList).toContain('risk-chip-low');
    expect(chip.nativeElement.textContent).toContain('LOW');
  });

  it('renders MEDIUM with the risk-chip-medium class', () => {
    setLevel('MEDIUM');
    const chip = fixture.debugElement.query(By.css('mat-chip'));
    expect(chip.nativeElement.classList).toContain('risk-chip-medium');
    expect(chip.nativeElement.textContent).toContain('MEDIUM');
  });

  it('renders HIGH with the risk-chip-high class', () => {
    setLevel('HIGH');
    const chip = fixture.debugElement.query(By.css('mat-chip'));
    expect(chip.nativeElement.classList).toContain('risk-chip-high');
    expect(chip.nativeElement.textContent).toContain('HIGH');
  });
});

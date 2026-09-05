import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { RuleContribution } from '../../../core/models/ai-risk-assessment.model';
import { RuleContributionsListComponent } from './rule-contributions-list.component';

describe('RuleContributionsListComponent', () => {
  let fixture: ComponentFixture<RuleContributionsListComponent>;
  let component: RuleContributionsListComponent;

  const setContributions = (contributions: RuleContribution[]) => {
    TestBed.configureTestingModule({ imports: [RuleContributionsListComponent] });
    fixture = TestBed.createComponent(RuleContributionsListComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('contributions', contributions);
    fixture.detectChanges();
  };

  it('sorts contributions by scoreContribution descending', () => {
    setContributions([
      { ruleId: 'r1', ruleName: 'Low weight rule', scoreContribution: 5 },
      { ruleId: 'r2', ruleName: 'High weight rule', scoreContribution: 25 },
      { ruleId: 'r3', ruleName: 'Mid weight rule', scoreContribution: 15 },
    ]);

    expect(component.sortedContributions.map((c) => c.ruleId)).toEqual(['r2', 'r3', 'r1']);
    const items = fixture.debugElement.queryAll(By.css('li'));
    expect(items[0].nativeElement.textContent).toContain('High weight rule');
    expect(items[0].nativeElement.textContent).toContain('+25');
  });

  it('shows an empty-state message when no rules fired', () => {
    setContributions([]);

    expect(fixture.debugElement.query(By.css('.empty-state')).nativeElement.textContent).toContain(
      'No rules fired for this assessment.',
    );
    expect(fixture.debugElement.query(By.css('ul'))).toBeFalsy();
  });
});

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { RiskAssessmentHistoryDialogComponent } from './risk-assessment-history-dialog.component';

describe('RiskAssessmentHistoryDialogComponent', () => {
  let fixture: ComponentFixture<RiskAssessmentHistoryDialogComponent>;
  let httpMock: HttpTestingController;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<RiskAssessmentHistoryDialogComponent>>;
  const customerId = 'customer-1';
  const transactionId = 'txn-1';
  const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 };

  beforeEach(() => {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [RiskAssessmentHistoryDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideNativeDateAdapter(),
        { provide: MAT_DIALOG_DATA, useValue: { customerId, transactionId } },
        { provide: MatDialogRef, useValue: dialogRefSpy },
      ],
    });
    fixture = TestBed.createComponent(RiskAssessmentHistoryDialogComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  function historyUrl(request: { url: string }): boolean {
    return request.url === `/api/v1/customers/${customerId}/ai-assessments`;
  }

  it('renders the history table scoped to the given transaction', () => {
    const req = httpMock.expectOne(historyUrl);
    expect(req.request.params.get('transactionId')).toBe(transactionId);
    req.flush(emptyPage);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Risk Assessment History');
  });

  it('closes the dialog when Close is clicked', () => {
    httpMock.expectOne(historyUrl).flush(emptyPage);
    fixture.detectChanges();

    fixture.debugElement.query(By.css('mat-dialog-actions button')).nativeElement.click();

    expect(dialogRefSpy.close).toHaveBeenCalled();
  });
});

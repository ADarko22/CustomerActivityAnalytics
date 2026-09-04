import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { CardTransaction } from '../../../core/models/transaction.model';
import { RiskAssessmentHistoryDialogComponent } from '../../risk-assessment/risk-assessment-history-dialog/risk-assessment-history-dialog.component';
import { TransactionDetailComponent } from './transaction-detail.component';

describe('TransactionDetailComponent', () => {
  let fixture: ComponentFixture<TransactionDetailComponent>;
  let httpMock: HttpTestingController;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  const cardTransaction: CardTransaction = {
    transactionId: 'txn-1',
    customerId: 'customer-1',
    activityType: 'CARD',
    amount: 42,
    currency: 'EUR',
    status: 'COMPLETED',
    createdAt: '2026-01-01T00:00:00Z',
    cardPan: '****1234',
    cardType: 'DEBIT',
    merchantName: 'Amazon',
    mccCode: '5732',
    cardPresent: true,
  };

  beforeEach(() => {
    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);
    TestBed.configureTestingModule({
      imports: [TransactionDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideNativeDateAdapter(),
        { provide: MatDialog, useValue: dialogSpy },
      ],
    });
    fixture = TestBed.createComponent(TransactionDetailComponent);
    fixture.componentRef.setInput('customerId', 'customer-1');
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('shows an empty-state prompt when nothing is selected', () => {
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Select a transaction');
  });

  it('renders two side-by-side cards: Transaction details and Risk Assessment', () => {
    fixture.componentInstance.transaction = cardTransaction;
    fixture.detectChanges();

    const cards = fixture.debugElement.queryAll(By.css('mat-card'));
    expect(cards.length).toBe(2);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Transaction txn-1');
    expect(text).toContain('Amazon');
    expect(text).toContain('DEBIT');
    expect(text).toContain('Risk Assessment');
    expect(text).toContain('Run AI Risk Assessment');
    expect(text).toContain('View Risk Assessments History');
  });

  it('opens the history dialog scoped to the transaction when the history button is clicked', () => {
    fixture.componentInstance.transaction = cardTransaction;
    fixture.detectChanges();

    const historyButton = fixture.debugElement
      .queryAll(By.css('button'))
      .find((btn) =>
        (btn.nativeElement as HTMLElement).textContent?.includes('View Risk Assessments History'),
      );
    historyButton?.nativeElement.click();

    expect(dialogSpy.open).toHaveBeenCalledWith(
      RiskAssessmentHistoryDialogComponent,
      jasmine.objectContaining({
        data: { customerId: 'customer-1', transactionId: 'txn-1' },
      }),
    );
  });
});

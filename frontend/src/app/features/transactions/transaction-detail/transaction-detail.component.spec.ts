import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { CardTransaction } from '../../../core/models/transaction.model';
import { TransactionDetailComponent } from './transaction-detail.component';

describe('TransactionDetailComponent', () => {
  let fixture: ComponentFixture<TransactionDetailComponent>;
  let httpMock: HttpTestingController;

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
    TestBed.configureTestingModule({
      imports: [TransactionDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideNativeDateAdapter(),
        provideRouter([]),
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

  it('renders card-specific fields and both AI risk assessment actions for a CARD transaction', () => {
    fixture.componentInstance.transaction = cardTransaction;
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Amazon');
    expect(text).toContain('DEBIT');
    expect(text).toContain('Run AI Risk Assessment');
    expect(text).toContain('View Risk Assessments History');
  });
});

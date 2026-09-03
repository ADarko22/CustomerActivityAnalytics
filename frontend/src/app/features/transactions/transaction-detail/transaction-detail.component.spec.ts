import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CardTransaction } from '../../../core/models/transaction.model';
import { TransactionDetailComponent } from './transaction-detail.component';

describe('TransactionDetailComponent', () => {
  let fixture: ComponentFixture<TransactionDetailComponent>;

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
    TestBed.configureTestingModule({ imports: [TransactionDetailComponent] });
    fixture = TestBed.createComponent(TransactionDetailComponent);
  });

  it('shows an empty-state prompt when nothing is selected', () => {
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Select a transaction');
  });

  it('renders card-specific fields for a CARD transaction', () => {
    fixture.componentInstance.transaction = cardTransaction;
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Amazon');
    expect(text).toContain('DEBIT');
  });
});

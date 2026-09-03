export type ActivityType = 'CARD' | 'PAYMENT' | 'CRYPTO';
export type TransactionStatus = 'COMPLETED' | 'PENDING' | 'FAILED' | 'REVERSED';

interface BaseTransaction {
  transactionId: string;
  customerId: string;
  amount: number;
  currency: string;
  status: TransactionStatus;
  createdAt: string;
}

export interface CardTransaction extends BaseTransaction {
  activityType: 'CARD';
  cardPan: string;
  cardType: string;
  merchantName: string;
  mccCode: string;
  cardPresent: boolean;
  authorizationCode?: string;
  declineReason?: string;
}

export interface PaymentTransaction extends BaseTransaction {
  activityType: 'PAYMENT';
  paymentMethod: string;
  senderAccount: string;
  receiverAccount: string;
  receiverBankCountry: string;
}

export interface CryptoTransaction extends BaseTransaction {
  activityType: 'CRYPTO';
  blockchain: string;
  walletAddressFrom: string;
  walletAddressTo: string;
  txHash: string;
  exchangeName?: string;
}

export type Transaction = CardTransaction | PaymentTransaction | CryptoTransaction;

export interface TransactionFilter {
  activityType?: ActivityType;
  status?: TransactionStatus;
  from?: string;
  to?: string;
  minAmount?: number;
  maxAmount?: number;
  currency?: string;
  cardType?: string;
  merchantName?: string;
  mccCode?: string;
  cardPresent?: boolean;
  paymentMethod?: string;
  senderAccount?: string;
  receiverAccount?: string;
  receiverBankCountry?: string;
  blockchain?: string;
  walletAddressFrom?: string;
  walletAddressTo?: string;
  exchangeName?: string;
}

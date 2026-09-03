import { ActivityType } from '../../../core/models/transaction.model';

export interface ColumnDef {
  key: string;
  label: string;
  filterType: 'text' | 'select' | 'boolean' | 'amount' | 'none';
  selectOptions?: string[];
}

export const COMMON_COLUMNS: ColumnDef[] = [
  { key: 'createdAt', label: 'Date', filterType: 'none' },
  { key: 'amount', label: 'Amount', filterType: 'amount' },
  { key: 'currency', label: 'Currency', filterType: 'text' },
  {
    key: 'status',
    label: 'Status',
    filterType: 'select',
    selectOptions: ['COMPLETED', 'PENDING', 'FAILED', 'REVERSED'],
  },
];

export const TYPE_COLUMNS: Record<ActivityType, ColumnDef[]> = {
  CARD: [
    { key: 'cardType', label: 'Card Type', filterType: 'text' },
    { key: 'merchantName', label: 'Merchant', filterType: 'text' },
    { key: 'mccCode', label: 'MCC', filterType: 'text' },
    { key: 'cardPresent', label: 'Card Present', filterType: 'boolean' },
  ],
  PAYMENT: [
    { key: 'paymentMethod', label: 'Method', filterType: 'text' },
    { key: 'senderAccount', label: 'Sender', filterType: 'text' },
    { key: 'receiverAccount', label: 'Receiver', filterType: 'text' },
    { key: 'receiverBankCountry', label: 'Country', filterType: 'text' },
  ],
  CRYPTO: [
    { key: 'blockchain', label: 'Blockchain', filterType: 'text' },
    { key: 'walletAddressFrom', label: 'From Wallet', filterType: 'text' },
    { key: 'walletAddressTo', label: 'To Wallet', filterType: 'text' },
    { key: 'exchangeName', label: 'Exchange', filterType: 'text' },
  ],
};

import type { FinancialTransaction } from '../../../services/studentApi';

export type AnalyticsTab = 'income' | 'expense';

export interface AnalyticsMonth {
  key: string;
  label: string;
  income: number;
  expense: number;
  withdrawal: number;
}

export interface AnalyticsCategory {
  label: string;
  amount: number;
  completedAmount?: number;
  pendingAmount?: number;
}

export interface AnalyticsCategoryMonth {
  key: string;
  label: string;
  rows: AnalyticsCategory[];
  total: number;
  pieBackground: string;
}

export interface WalletAnalyticsData {
  transactions: FinancialTransaction[];
  monthly: AnalyticsMonth[];
  completedIncome: number;
  totalIncome: number;
  pendingIncome: number;
  totalExpense: number;
  totalWithdrawals: number;
  pendingWithdrawals: number;
  completedWithdrawals: number;
  refunds: number;
  outflowTransactions: FinancialTransaction[];
  categoryRows: AnalyticsCategory[];
  incomeCategoryRows: AnalyticsCategory[];
  incomeTransactions: FinancialTransaction[];
  largest: number;
  totalExpenseCategories: number;
  pieBackground: string;
  incomePieBackground: string;
}

export const analyticsMoney = (value: number) =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0
  }).format(value);

export function expenseLabel(item: FinancialTransaction) {
  const text = `${item.type} ${item.description ?? ''}`.toLowerCase();
  if (text.includes('tuition') || text.includes('học phí') || text.includes('installment')) return 'Học phí hợp đồng';
  if (text.includes('studying') || text.includes('học')) return 'Phí yêu cầu học';
  if (text.includes('teaching') || text.includes('dạy')) return 'Phí yêu cầu dạy';
  if (text.includes('withdraw')) return 'Rút tiền';
  return item.type === 'DEBIT' ? 'Chi tiêu khác' : item.type.replaceAll('_', ' ');
}

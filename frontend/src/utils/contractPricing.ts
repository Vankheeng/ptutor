import type { Contract } from '../services/studentApi';

export function pricePerLesson(contract: Pick<Contract, 'price' | 'paymentPeriod' | 'totalLessons'>) {
  return contract.price;
}

export function formatLessonPrice(contract: Pick<Contract, 'price' | 'paymentPeriod' | 'totalLessons'>) {
  return `${new Intl.NumberFormat('vi-VN').format(pricePerLesson(contract))} VNĐ / buổi`;
}

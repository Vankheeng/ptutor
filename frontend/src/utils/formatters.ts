export function formatCurrency(value: number | null | undefined): string {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(
    value ?? 0
  );
}

export function getInitials(value: string | null | undefined): string {
  return (value ?? 'P')
    .split(' ')
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0])
    .join('')
    .toUpperCase();
}

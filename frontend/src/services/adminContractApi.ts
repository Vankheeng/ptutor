import { get } from './httpClient';
import type { Contract, ContractStatus, PageResponse } from './studentApi';

const basePath = '/api/v1/admin/contracts';

export function getAdminContracts(
  filters: { status?: ContractStatus; keyword?: string; page: number },
  signal?: AbortSignal
) {
  const params = new URLSearchParams({ page: String(filters.page), size: '20' });
  if (filters.status) params.set('status', filters.status);
  if (filters.keyword) params.set('keyword', filters.keyword);
  return get<PageResponse<Contract>>(`${basePath}?${params}`, { signal });
}

export function getAdminContract(id: string, signal?: AbortSignal) {
  return get<Contract>(`${basePath}/${id}`, { signal });
}

import { useCallback, useState } from 'react';
import { useResource } from '../../hooks/useResource';
import { getAdminStudentContract, getAdminStudentContracts } from '../../services/adminStudentApi';
import type { ContractStatus } from '../../services/adminTutorApi';
import { formatCurrency } from '../../utils/formatters';
import {
  contractStatusMeta,
  formatDate,
  formatDateTime,
  fullName,
  paymentPeriodLabels,
  teachingModeLabels
} from './adminFormat';
import { useAdminStudent } from './adminStudentContext';
import {
  AdminModal,
  AdminPagination,
  AdminState,
  DefinitionGrid,
  FilterChips,
  StatusBadge
} from './components/AdminUi';

type StatusFilter = ContractStatus | 'ALL';

export function AdminStudentContractsPage() {
  const { account } = useAdminStudent();
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [page, setPage] = useState(0);
  const [openId, setOpenId] = useState<string | null>(null);
  const load = useCallback(
    (signal: AbortSignal) =>
      getAdminStudentContracts(account.userId, status === 'ALL' ? undefined : status, page, signal),
    [account.userId, page, status]
  );
  const { data, error, isLoading } = useResource(load);

  return (
    <section className="admin-card">
      <div className="admin-card-toolbar">
        <h2>Hợp đồng</h2>
        <FilterChips<StatusFilter>
          label="Lọc theo trạng thái"
          value={status}
          options={[
            { value: 'ALL', label: 'Tất cả' },
            ...(Object.keys(contractStatusMeta) as ContractStatus[]).map((value) => ({
              value,
              label: contractStatusMeta[value].label
            }))
          ]}
          onChange={(value) => {
            setStatus(value);
            setPage(0);
          }}
        />
      </div>
      {!data ? (
        <div className="admin-card-pad">
          <AdminState loading={isLoading} error={error} />
        </div>
      ) : data.content.length === 0 ? (
        <AdminState empty="Chưa có hợp đồng nào." />
      ) : (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Gia sư</th>
                <th>Môn · Lớp</th>
                <th>Hình thức</th>
                <th>Học phí</th>
                <th>Số buổi</th>
                <th>Thời gian</th>
                <th>Trạng thái</th>
                <th>
                  <span className="admin-sr-only">Thao tác</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((contract) => (
                <tr key={contract.id}>
                  <td>
                    <div className="admin-stack">
                      <strong>{fullName(contract.tutorFirstName, contract.tutorLastName, contract.tutorEmail)}</strong>
                      <span>{contract.tutorEmail}</span>
                    </div>
                  </td>
                  <td>
                    {contract.subjectName} · {contract.gradeName}
                  </td>
                  <td>{teachingModeLabels[contract.teachingMode]}</td>
                  <td className="admin-nowrap">
                    {formatCurrency(contract.price)} {paymentPeriodLabels[contract.paymentPeriod]}
                  </td>
                  <td>{contract.totalLessons ?? '—'}</td>
                  <td className="admin-nowrap">
                    {formatDate(contract.startDate)} – {formatDate(contract.endDate)}
                  </td>
                  <td>
                    <StatusBadge meta={contractStatusMeta[contract.status]} />
                  </td>
                  <td className="admin-cell-action">
                    <button
                      className="admin-button small secondary"
                      type="button"
                      onClick={() => setOpenId(contract.id)}
                    >
                      Chi tiết
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && (
        <div className="admin-card-footer">
          <AdminPagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            unit="hợp đồng"
            onChange={setPage}
          />
        </div>
      )}
      {openId && <ContractModal userId={account.userId} contractId={openId} onClose={() => setOpenId(null)} />}
    </section>
  );
}

function ContractModal({ userId, contractId, onClose }: { userId: string; contractId: string; onClose: () => void }) {
  const load = useCallback(
    (signal: AbortSignal) => getAdminStudentContract(userId, contractId, signal),
    [contractId, userId]
  );
  const { data: contract, error, isLoading } = useResource(load);

  return (
    <AdminModal
      title={contract ? `Hợp đồng ${contract.subjectName} · ${contract.gradeName}` : 'Hợp đồng'}
      subtitle={contract && <StatusBadge meta={contractStatusMeta[contract.status]} />}
      onClose={onClose}
    >
      <div className="admin-modal-pad">
        {!contract ? (
          <AdminState loading={isLoading} error={error} />
        ) : (
          <DefinitionGrid
            columns={3}
            items={[
              ['Gia sư', fullName(contract.tutorFirstName, contract.tutorLastName)],
              ['Email gia sư', contract.tutorEmail],
              ['Hình thức', teachingModeLabels[contract.teachingMode]],
              ['Học phí', `${formatCurrency(contract.price)} ${paymentPeriodLabels[contract.paymentPeriod]}`],
              ['Tổng số buổi', contract.totalLessons ?? '—'],
              ['Lịch mong muốn', contract.preferredSchedule || '—'],
              ['Bắt đầu', formatDate(contract.startDate)],
              ['Kết thúc', formatDate(contract.endDate)],
              ['Ngày ký', formatDateTime(contract.signedAt)],
              ['Gia hạn từ hợp đồng', contract.renewedFromContractId ? 'Có' : 'Không'],
              [
                'Nguồn',
                contract.studentTutorRequestId
                  ? 'Học viên ứng tuyển vào tin dạy'
                  : contract.tutorStudentRequestId
                    ? 'Gia sư đề xuất dạy'
                    : '—'
              ],
              ['Tạo lúc', formatDateTime(contract.createdAt)]
            ]}
          />
        )}
      </div>
    </AdminModal>
  );
}

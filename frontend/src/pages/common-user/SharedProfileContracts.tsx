import { useCallback, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getSharedContracts, type Contract, type ContractStatus } from '../../services/studentApi';
import { formatCurrency } from '../../utils/formatters';
import {
  contractStatusMeta,
  formatDate,
  formatDateTime,
  fullName,
  paymentPeriodLabels,
  teachingModeLabels
} from '../admin/adminFormat';
import {
  AdminModal,
  AdminPagination,
  AdminState,
  DefinitionGrid,
  FilterChips,
  StatusBadge
} from '../admin/components/AdminUi';

type StatusFilter = ContractStatus | 'ALL';

function populated(items: Array<[string, ReactNode | null | undefined]>) {
  return items.filter(
    ([, value]) => value !== null && value !== undefined && (typeof value !== 'string' || value.trim() !== '')
  ) as [string, ReactNode][];
}

export function SharedProfileContracts({
  counterpartyId,
  viewerRole
}: {
  counterpartyId: string;
  viewerRole: 'student' | 'tutor';
}) {
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Contract | null>(null);
  const queryKey = `${counterpartyId}:${status}:${page}`;
  const load = useCallback(
    async (signal: AbortSignal) => ({
      key: queryKey,
      response: await getSharedContracts(counterpartyId, status === 'ALL' ? undefined : status, page, 10, signal)
    }),
    [counterpartyId, status, page, queryKey]
  );
  const query = useResource(load);
  const data = query.data?.key === queryKey ? query.data.response : null;
  const isLoading = query.isLoading || (query.data !== null && query.data.key !== queryKey);
  const error = query.error;
  const contractPath = viewerRole === 'student' ? '/student/contracts' : '/tutor/sections/contracts';

  return (
    <section className="admin-card profile-contracts">
      <div className="admin-card-toolbar">
        <h2>Hợp đồng chung</h2>
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
        <AdminState empty="Không có hợp đồng ở trạng thái này." />
      ) : (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>{viewerRole === 'student' ? 'Gia sư' : 'Học viên'}</th>
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
                      <strong>
                        {viewerRole === 'student'
                          ? fullName(contract.tutorFirstName, contract.tutorLastName, contract.tutorEmail)
                          : fullName(contract.studentFirstName, contract.studentLastName, contract.studentEmail)}
                      </strong>
                      <span>{viewerRole === 'student' ? contract.tutorEmail : contract.studentEmail}</span>
                    </div>
                  </td>
                  <td>{[contract.subjectName, contract.gradeName].filter(Boolean).join(' · ')}</td>
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
                      onClick={() => setSelected(contract)}
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
      {selected && (
        <AdminModal
          title={`Hợp đồng ${[selected.subjectName, selected.gradeName].filter(Boolean).join(' · ')}`}
          subtitle={<StatusBadge meta={contractStatusMeta[selected.status]} />}
          onClose={() => setSelected(null)}
        >
          <div className="admin-modal-pad profile-contract-modal">
            <DefinitionGrid
              columns={3}
              items={populated([
                [
                  viewerRole === 'student' ? 'Gia sư' : 'Học viên',
                  viewerRole === 'student'
                    ? fullName(selected.tutorFirstName, selected.tutorLastName)
                    : fullName(selected.studentFirstName, selected.studentLastName)
                ],
                ['Hình thức', teachingModeLabels[selected.teachingMode]],
                ['Học phí', `${formatCurrency(selected.price)} ${paymentPeriodLabels[selected.paymentPeriod]}`],
                ['Tổng số buổi', selected.totalLessons],
                ['Lịch học', selected.preferredSchedule],
                ['Bắt đầu', selected.startDate ? formatDate(selected.startDate) : null],
                ['Kết thúc', selected.endDate ? formatDate(selected.endDate) : null],
                ['Ngày ký', selected.signedAt ? formatDateTime(selected.signedAt) : null],
                ['Tạo lúc', selected.createdAt ? formatDateTime(selected.createdAt) : null]
              ])}
            />
            <Link className="admin-button small profile-contract-open" to={`${contractPath}/${selected.id}`}>
              Xem hợp đồng
            </Link>
          </div>
        </AdminModal>
      )}
    </section>
  );
}

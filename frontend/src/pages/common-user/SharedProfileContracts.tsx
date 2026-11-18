import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { ProfileNameLink } from '../../components/common-user/ProfileNameLink';
import { useResource } from '../../hooks/useResource';
import { getSharedContracts, type ContractStatus } from '../../services/studentApi';
import { formatCurrency } from '../../utils/formatters';
import {
  contractStatusMeta,
  formatDate,
  fullName,
  paymentPeriodLabels,
  teachingModeLabels
} from '../admin/adminFormat';
import { AdminPagination, AdminState, FilterChips, StatusBadge } from '../admin/components/AdminUi';

type StatusFilter = ContractStatus | 'ALL';

export function SharedProfileContracts({
  counterpartyId,
  viewerRole
}: {
  counterpartyId: string;
  viewerRole: 'student' | 'tutor';
}) {
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [page, setPage] = useState(0);
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
                        <ProfileNameLink
                          role={viewerRole === 'student' ? 'tutor' : 'student'}
                          profileId={viewerRole === 'student' ? contract.tutorId : contract.studentId}
                        >
                          {viewerRole === 'student'
                            ? fullName(contract.tutorFirstName, contract.tutorLastName, contract.tutorEmail)
                            : fullName(contract.studentFirstName, contract.studentLastName, contract.studentEmail)}
                        </ProfileNameLink>
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
                    <Link className="admin-button small secondary" to={`${contractPath}/${contract.id}`}>
                      Chi tiết
                    </Link>
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
    </section>
  );
}

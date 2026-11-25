import { useCallback, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getAdminContract, getAdminContracts } from '../../services/adminContractApi';
import type { Contract, ContractStatus } from '../../services/studentApi';
import { formatCurrency } from '../../utils/formatters';
import {
  contractStatusMeta,
  formatDate,
  formatDateTime,
  fullName,
  paymentPeriodLabels,
  teachingModeLabels
} from './adminFormat';
import { AdminModal, AdminPagination, AdminState, FilterChips, StatusBadge } from './components/AdminUi';
import './admin-contracts.css';

const statuses: Array<ContractStatus | 'ALL'> = ['ALL', 'PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED'];

export function AdminContractsPage() {
  const [params, setParams] = useSearchParams();
  const status = statuses.find((item) => item === params.get('status')) ?? 'ALL';
  const keyword = params.get('keyword') ?? '';
  const pageValue = Number(params.get('page') ?? 0);
  const page = Number.isSafeInteger(pageValue) && pageValue >= 0 ? pageValue : 0;
  const [openId, setOpenId] = useState<string | null>(null);
  const load = useCallback(
    (signal: AbortSignal) =>
      getAdminContracts({ status: status === 'ALL' ? undefined : status, keyword, page }, signal),
    [keyword, page, status]
  );
  const { data, error, isLoading } = useResource(load);
  const update = (changes: { status?: string; keyword?: string; page?: number }) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) => {
      if (value === undefined || value === '' || value === 0) next.delete(key);
      else next.set(key, String(value));
    });
    setParams(next);
  };
  const search = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    update({ keyword: String(new FormData(event.currentTarget).get('keyword') ?? '').trim(), page: 0 });
  };

  return (
    <div className="admin-page admin-contracts-page">
      <h1 className="admin-title">Quản lý hợp đồng</h1>
      <FilterChips<ContractStatus | 'ALL'>
        label="Trạng thái hợp đồng"
        value={status}
        options={statuses.map((value) => ({
          value,
          label: value === 'ALL' ? 'Tất cả' : contractStatusMeta[value].label
        }))}
        onChange={(value) => update({ status: value === 'ALL' ? undefined : value, page: 0 })}
      />
      <section className="admin-card">
        <form className="admin-filters" onSubmit={search}>
          <label className="admin-filter-grow">
            Tìm hợp đồng
            <input
              key={keyword}
              name="keyword"
              type="search"
              defaultValue={keyword}
              placeholder="Tên, email, môn học hoặc lớp"
            />
          </label>
          <button className="admin-button" type="submit">
            Tìm kiếm
          </button>
          {keyword && (
            <button
              className="admin-button secondary"
              type="button"
              onClick={() => update({ keyword: undefined, page: 0 })}
            >
              Xóa lọc
            </button>
          )}
        </form>
        {!data ? (
          <div className="admin-card-pad">
            <AdminState loading={isLoading} error={error} />
          </div>
        ) : data.content.length === 0 ? (
          <AdminState empty="Không tìm thấy hợp đồng phù hợp." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table admin-contracts-table">
              <thead>
                <tr>
                  <th>Hợp đồng</th>
                  <th>Học viên</th>
                  <th>Gia sư</th>
                  <th>Thời gian</th>
                  <th>Học phí</th>
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
                          {contract.subjectName} - {contract.gradeName}
                        </strong>
                        <span>HD-{contract.id.slice(0, 8).toUpperCase()}</span>
                        <span>{teachingModeLabels[contract.teachingMode]}</span>
                      </div>
                    </td>
                    <td>
                      <div className="admin-stack">
                        <strong>{fullName(contract.studentFirstName, contract.studentLastName)}</strong>
                        <span>{contract.studentEmail}</span>
                      </div>
                    </td>
                    <td>
                      <div className="admin-stack">
                        <strong>{fullName(contract.tutorFirstName, contract.tutorLastName)}</strong>
                        <span>{contract.tutorEmail}</span>
                      </div>
                    </td>
                    <td className="admin-nowrap">
                      {formatDate(contract.startDate)} – {formatDate(contract.endDate)}
                    </td>
                    <td className="admin-nowrap">
                      {formatCurrency(contract.price)} {paymentPeriodLabels[contract.paymentPeriod]}
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
              onChange={(value) => update({ page: value })}
            />
          </div>
        )}
      </section>
      {openId && <AdminContractModal id={openId} onClose={() => setOpenId(null)} />}
    </div>
  );
}

function AdminContractModal({ id, onClose }: { id: string; onClose: () => void }) {
  const load = useCallback((signal: AbortSignal) => getAdminContract(id, signal), [id]);
  const { data: contract, error, isLoading } = useResource(load);
  return (
    <AdminModal
      size="xl"
      title={contract ? `${contract.subjectName} - ${contract.gradeName}` : 'Chi tiết hợp đồng'}
      subtitle={contract && <StatusBadge meta={contractStatusMeta[contract.status]} />}
      onClose={onClose}
    >
      <div className="admin-modal-pad admin-contract-detail">
        {!contract ? <AdminState loading={isLoading} error={error} /> : <ContractDetails contract={contract} />}
      </div>
    </AdminModal>
  );
}

function ContractDetails({ contract }: { contract: Contract }) {
  const days = ['', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ nhật'];
  const schedule = contract.schedules?.length
    ? contract.schedules
        .map(
          (item) => `${days[item.dayOfWeek] || 'Ngày khác'} ${item.startTime.slice(0, 5)}–${item.endTime.slice(0, 5)}`
        )
        .join(', ')
    : contract.preferredSchedule || '—';
  return (
    <>
      <div className="admin-contract-detail-id">
        <span>Mã hợp đồng</span>
        <strong>HD-{contract.id.slice(0, 8).toUpperCase()}</strong>
      </div>
      <div className="admin-contract-parties">
        <Link className="admin-contract-party-card" to={`/admin/students/${contract.studentUserId}`}>
          <h3>Học viên</h3>
          <strong>{fullName(contract.studentFirstName, contract.studentLastName)}</strong>
          <span>{contract.studentEmail}</span>
          {contract.teachingMode === 'OFFLINE' && contract.studentDetailAddress && (
            <span>{contract.studentDetailAddress}</span>
          )}
        </Link>
        <Link className="admin-contract-party-card" to={`/admin/tutors/${contract.tutorUserId}`}>
          <h3>Gia sư</h3>
          <strong>{fullName(contract.tutorFirstName, contract.tutorLastName)}</strong>
          <span>{contract.tutorEmail}</span>
        </Link>
      </div>
      <section className="admin-contract-detail-section">
        <h3>Điều khoản học tập</h3>
        <dl>
          <Detail label="Môn học" value={contract.subjectName} />
          <Detail label="Lớp" value={contract.gradeName} />
          <Detail label="Hình thức" value={teachingModeLabels[contract.teachingMode]} />
          <Detail
            label="Học phí"
            value={`${formatCurrency(contract.price)} ${paymentPeriodLabels[contract.paymentPeriod]}`}
          />
          <Detail label="Số buổi" value={`${contract.totalLessons} buổi`} />
          <Detail label="Thời gian" value={`${formatDate(contract.startDate)} – ${formatDate(contract.endDate)}`} />
          <Detail label="Lịch học" value={schedule} wide />
        </dl>
      </section>
      <section className="admin-contract-detail-section">
        <h3>Mốc thời gian</h3>
        <dl>
          <Detail label="Ngày tạo" value={formatDateTime(contract.createdAt)} />
          <Detail label="Ngày ký" value={formatDateTime(contract.signedAt)} />
          <Detail label="Cập nhật lần cuối" value={formatDateTime(contract.updatedAt)} />
          <Detail
            label="Gia hạn từ"
            value={
              contract.renewedFromContractId
                ? `HD-${contract.renewedFromContractId.slice(0, 8).toUpperCase()}`
                : 'Không'
            }
          />
        </dl>
      </section>
    </>
  );
}

function Detail({ label, value, wide = false }: { label: string; value: string; wide?: boolean }) {
  return (
    <div className={wide ? 'wide' : undefined}>
      <dt>{label}</dt>
      <dd>{value || '—'}</dd>
    </div>
  );
}

import { useCallback, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { ApiError } from '../../services/httpClient';
import {
  cancelContract,
  getContract,
  getInstallments,
  payInstallment,
  rejectContract,
  renewContract,
  signContract,
  updateContract,
  type Contract,
  type ContractTerms,
  type Installment
} from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';
import { dateLabel, moneyLabel, paymentPeriodLabel, statusLabel } from '../../utils/labels';

export function StudentContractDetailPage() {
  const { contractId = '' } = useParams();
  const { user } = useAuth();
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const load = useCallback(async () => {
    const item = await getContract(contractId);
    const installments = item.status === 'ACTIVE' ? await getInstallments(contractId) : [];
    return { item, installments };
  }, [contractId]);
  const query = useResource(load, revision);
  const contract: Contract | null = query.data?.item ?? null;
  const installments: Installment[] = query.data?.installments ?? [];
  const action = async (callback: () => Promise<unknown>, message: string) => {
    setBusy(true);
    setError('');
    try {
      await callback();
      setNotice(message);
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : caught instanceof Error
            ? caught.message
            : 'Chưa cập nhật được hợp đồng.'
      );
    } finally {
      setBusy(false);
    }
  };
  const edit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!contract) return;
    const values = new FormData(event.currentTarget);
    const payload = {
      price: Number(values.get('price')),
      preferredSchedule: String(values.get('preferredSchedule') ?? ''),
      startDate: String(values.get('startDate') ?? ''),
      endDate: String(values.get('endDate') ?? '')
    };
    await action(() => updateContract(contract.id, payload), 'Đã cập nhật hợp đồng.');
  };
  const renew = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!contract) return;
    const values = new FormData(event.currentTarget);
    const terms: ContractTerms = {
      price: Number(values.get('price')),
      paymentPeriod: contract.paymentPeriod,
      totalLessons: Number(values.get('totalLessons')),
      preferredSchedule: String(values.get('preferredSchedule') ?? ''),
      startDate: String(values.get('startDate') ?? ''),
      endDate: String(values.get('endDate') ?? '')
    };
    await action(() => renewContract(contract.id, terms), 'Đã gửi đề xuất gia hạn.');
  };
  const pay = async (installmentId: string) => {
    setBusy(true);
    setError('');
    try {
      const payment = await payInstallment(contractId, installmentId);
      window.open(payment.paymentUrl, '_blank', 'noopener,noreferrer');
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : caught instanceof Error
            ? caught.message
            : 'Chưa tạo được giao dịch thanh toán.'
      );
    } finally {
      setBusy(false);
    }
  };
  if (!contract)
    return query.error ? (
      <div className="student-error" role="alert">
        {query.error}
      </div>
    ) : (
      <div className="student-empty">
        <h3>Đang tải hợp đồng…</h3>
      </div>
    );
  return (
    <>
      <Link className="student-link" to="/student/contracts">
        ← Quay lại danh sách hợp đồng
      </Link>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">CHI TIẾT HỢP ĐỒNG</span>
          <h1>
            {contract.subjectName} · {contract.gradeName}
          </h1>
          <p>Gia sư: {[contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'chưa rõ'}</p>
        </div>
        <span className={`student-status ${contract.status.toLowerCase()}`}>
          {statusLabel(contract.status, 'contract')}
        </span>
      </div>
      {notice && (
        <div className="student-notice" role="status">
          {notice}
        </div>
      )}
      {error && (
        <div className="student-error" role="alert">
          {error}
        </div>
      )}
      <section className="student-panel">
        <div className="student-detail-facts">
          <span>
            <small>Học phí</small>
            <strong>{formatMoney(contract.price)}</strong>
          </span>
          <span>
            <small>Kỳ thanh toán</small>
            <strong>{paymentPeriodLabel(contract.paymentPeriod)}</strong>
          </span>
          <span>
            <small>Số buổi</small>
            <strong>{contract.totalLessons}</strong>
          </span>
          <span>
            <small>Lịch học</small>
            <strong>{contract.preferredSchedule}</strong>
          </span>
          <span>
            <small>Thời gian</small>
            <strong>
              {dateLabel(contract.startDate)} → {dateLabel(contract.endDate)}
            </strong>
          </span>
          <span>
            <small>Người tạo</small>
            <strong>{contract.createdByUserId === user?.userId ? 'Bạn' : 'Gia sư'}</strong>
          </span>
        </div>
        <div className="student-form-actions">
          {contract.status === 'PENDING' && contract.createdByUserId !== user?.userId && (
            <button
              className="student-button"
              disabled={busy}
              type="button"
              onClick={() => void action(() => signContract(contract.id), 'Đã ký hợp đồng.')}
            >
              Ký hợp đồng
            </button>
          )}
          {contract.status === 'PENDING' && contract.createdByUserId !== user?.userId && (
            <button
              className="student-button secondary"
              disabled={busy}
              type="button"
              onClick={() => void action(() => rejectContract(contract.id), 'Đã từ chối hợp đồng.')}
            >
              Từ chối
            </button>
          )}
          {contract.status === 'PENDING' && contract.createdByUserId === user?.userId && (
            <button
              className="student-button secondary"
              disabled={busy}
              type="button"
              onClick={() => void action(() => cancelContract(contract.id), 'Đã huỷ hợp đồng.')}
            >
              Huỷ hợp đồng
            </button>
          )}
        </div>
      </section>
      {contract.status === 'PENDING' && contract.createdByUserId === user?.userId && (
        <section className="student-panel" style={{ marginTop: 15 }}>
          <h2>Sửa điều khoản hợp đồng</h2>
          <form className="student-form-grid" onSubmit={(event) => void edit(event)}>
            <label>
              Học phí
              <input name="price" type="number" min="0" defaultValue={contract.price} />
            </label>
            <label>
              Lịch học mong muốn
              <input name="preferredSchedule" defaultValue={contract.preferredSchedule} required />
            </label>
            <label>
              Ngày bắt đầu
              <input name="startDate" type="date" defaultValue={contract.startDate} required />
            </label>
            <label>
              Ngày kết thúc
              <input name="endDate" type="date" defaultValue={contract.endDate} required />
            </label>
            <div className="student-form-actions">
              <button className="student-button" disabled={busy} type="submit">
                Lưu thay đổi
              </button>
            </div>
          </form>
        </section>
      )}
      {contract.status === 'ACTIVE' && (
        <section className="student-panel" style={{ marginTop: 15 }}>
          <h2>Các đợt thanh toán</h2>
          {installments.length ? (
            <div className="student-list">
              {installments.map((item) => (
                <div className="student-list-row" key={item.id}>
                  <div>
                    <h3>Đợt {item.sequenceNumber}</h3>
                    <p>
                      Hạn {dateLabel(item.dueDate)} · {formatMoney(item.amount)}
                    </p>
                  </div>
                  <div>
                    <span className={`student-status ${item.status.toLowerCase()}`}>
                      {statusLabel(item.status, 'payment')}
                    </span>
                    {item.status === 'PENDING' && (
                      <button
                        className="student-button"
                        style={{ marginTop: 8 }}
                        type="button"
                        disabled={busy}
                        onClick={() => void pay(item.id)}
                      >
                        Thanh toán qua VNPay
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="student-muted">Chưa có đợt thanh toán nào.</p>
          )}
        </section>
      )}
      {contract.status === 'ACTIVE' && (
        <section className="student-panel" style={{ marginTop: 15 }}>
          <h2>Gia hạn hợp đồng</h2>
          <form className="student-form-grid" onSubmit={(event) => void renew(event)}>
            <label>
              Học phí
              <input name="price" type="number" min="0" defaultValue={contract.price} required />
            </label>
            <label>
              Tổng số buổi
              <input name="totalLessons" type="number" min="1" defaultValue={contract.totalLessons} required />
            </label>
            <label>
              Ngày bắt đầu
              <input name="startDate" type="date" required />
            </label>
            <label>
              Ngày kết thúc
              <input name="endDate" type="date" required />
            </label>
            <label className="student-field-full">
              Lịch học mong muốn
              <input name="preferredSchedule" defaultValue={contract.preferredSchedule} required />
            </label>
            <div className="student-form-actions">
              <button className="student-button" disabled={busy} type="submit">
                Gửi đề xuất gia hạn
              </button>
            </div>
          </form>
        </section>
      )}
    </>
  );
}
function formatMoney(value: number) {
  return moneyLabel(value);
}

import { useCallback, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  approveWithdrawal,
  completeWithdrawal,
  getAdminWithdrawal,
  rejectWithdrawal,
  type WithdrawalStatus
} from '../../services/adminWithdrawalApi';
import { AdminAvatar, AdminModal, AdminState, DefinitionGrid, ReasonField, StatusBadge } from './components/AdminUi';
import { formatWithdrawalDate, formatWithdrawalMoney, withdrawalLabels } from './withdrawalFormat';
import { WithdrawalTransferQr } from './WithdrawalTransferQr';
import './withdrawals.css';

type Dialog = 'approve' | 'reject' | 'complete' | null;
const statusTone: Record<WithdrawalStatus, 'pending' | 'info' | 'success' | 'danger' | 'neutral'> = {
  PENDING: 'pending',
  PROCESSING: 'info',
  COMPLETED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'neutral'
};

export function AdminWithdrawalDetailPage() {
  const { withdrawalId = '' } = useParams();
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [reason, setReason] = useState('');
  const [reasonTried, setReasonTried] = useState(false);
  const [reference, setReference] = useState('');
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);
  const load = useCallback((signal: AbortSignal) => getAdminWithdrawal(withdrawalId, signal), [withdrawalId]);
  const { data, error, isLoading } = useResource(load, revision);

  const run = async (action: () => Promise<unknown>, success: string) => {
    setBusy(true);
    setMessage(null);
    try {
      await action();
      setDialog(null);
      setMessage({ tone: 'success', text: success });
      setRevision((value) => value + 1);
    } catch (cause) {
      setMessage({
        tone: 'danger',
        text: cause instanceof Error ? cause.message : 'Không thể xử lý yêu cầu. Vui lòng thử lại.'
      });
      setRevision((value) => value + 1);
    } finally {
      setBusy(false);
    }
  };

  if (!data)
    return (
      <div className="admin-page">
        <AdminState loading={isLoading} error={error} />
      </div>
    );
  const { withdrawal } = data;
  const pending = withdrawal.status === 'PENDING';
  const processing = withdrawal.status === 'PROCESSING';
  const recipient = data.userName || data.userEmail;

  return (
    <div className="admin-page admin-withdrawal-page">
      <Link className="admin-back" to="/admin/withdrawals">
        ‹ Danh sách yêu cầu rút tiền
      </Link>
      <header className="admin-withdrawal-heading">
        <div>
          <div className="admin-tags">
            <StatusBadge meta={{ label: withdrawalLabels[withdrawal.status], tone: statusTone[withdrawal.status] }} />
          </div>
          <h1 className="admin-title">Yêu cầu rút tiền</h1>
          <div className="admin-withdrawal-meta">
            <span>Tạo lúc {formatWithdrawalDate(withdrawal.createdAt)}</span>
            <span>Mã yêu cầu {withdrawalId.slice(0, 8).toUpperCase()}</span>
          </div>
        </div>
        <div className="admin-withdrawal-total">
          <span>Số tiền rút</span>
          <strong>{formatWithdrawalMoney(withdrawal.amount)}</strong>
        </div>
      </header>
      {message && (
        <div
          className={`admin-alert admin-alert-${message.tone}`}
          role={message.tone === 'danger' ? 'alert' : 'status'}
        >
          {message.text}
        </div>
      )}

      <div className="admin-columns">
        <div className="admin-column-main">
          {processing && data.accountNumber && (
            <section className="admin-card admin-card-pad">
              <div className="admin-section-heading">
                <h2>Chuyển khoản cho người rút</h2>
                <span>Bước 2 / 2</span>
              </div>
              <WithdrawalTransferQr
                bankCode={withdrawal.bankCode}
                accountNumber={data.accountNumber}
                accountHolderName={withdrawal.accountHolderName}
                amount={withdrawal.amount}
                withdrawalId={withdrawalId}
              />
            </section>
          )}

          <section className="admin-card admin-card-pad">
            <div className="admin-section-heading">
              <h2>Tài khoản nhận tiền</h2>
              <span>Thông tin do người rút cung cấp</span>
            </div>
            <div className="admin-withdrawal-bank">
              <div className="admin-withdrawal-bank-symbol" aria-hidden="true">
                {withdrawal.bankName.slice(0, 1)}
              </div>
              <div>
                <strong>{withdrawal.bankName}</strong>
                <span>{withdrawal.bankCode}</span>
              </div>
            </div>
            <DefinitionGrid
              items={[
                [
                  'Số tài khoản',
                  <strong className="admin-withdrawal-account">
                    {data.accountNumber || withdrawal.maskedAccountNumber}
                  </strong>
                ],
                ['Chủ tài khoản', withdrawal.accountHolderName]
              ]}
            />
          </section>

          <section className="admin-card admin-card-pad">
            <h2>Thông tin yêu cầu</h2>
            <div className="admin-person">
              <AdminAvatar name={recipient} />
              <div>
                <strong>{recipient}</strong>
                <span>{data.userEmail}</span>
              </div>
            </div>
            <DefinitionGrid
              items={[
                ['Ngày tạo', formatWithdrawalDate(withdrawal.createdAt)],
                ['Ngày duyệt', formatWithdrawalDate(withdrawal.reviewedAt)]
              ]}
            />
            {withdrawal.note && (
              <div className="admin-withdrawal-note">
                <span>Ghi chú của người rút</span>
                <p>{withdrawal.note}</p>
              </div>
            )}
            {withdrawal.rejectionReason && (
              <div className="admin-alert admin-alert-danger">Lý do từ chối: {withdrawal.rejectionReason}</div>
            )}
            {withdrawal.transferReference && (
              <DefinitionGrid
                items={[
                  ['Mã giao dịch ngân hàng', withdrawal.transferReference],
                  ['Hoàn tất lúc', formatWithdrawalDate(withdrawal.completedAt)]
                ]}
              />
            )}
          </section>
        </div>

        <aside className="admin-column-side">
          <section className="admin-card admin-card-pad">
            <h2>{pending ? 'Xử lý yêu cầu' : processing ? 'Hoàn tất chuyển khoản' : 'Trạng thái xử lý'}</h2>
            <ol className="admin-withdrawal-steps">
              <li className="done">
                <strong>Gửi yêu cầu</strong>
                <span>{formatWithdrawalDate(withdrawal.createdAt)}</span>
              </li>
              {withdrawal.status === 'REJECTED' ? (
                <li className="done">
                  <strong>Từ chối</strong>
                  <span>{formatWithdrawalDate(withdrawal.reviewedAt)}</span>
                </li>
              ) : withdrawal.status === 'CANCELLED' ? (
                <li className="done">
                  <strong>Người rút hủy yêu cầu</strong>
                </li>
              ) : (
                <>
                  <li className={pending ? 'current' : 'done'}>
                    <strong>Duyệt yêu cầu</strong>
                    <span>{pending ? 'Chờ kiểm tra thông tin' : formatWithdrawalDate(withdrawal.reviewedAt)}</span>
                  </li>
                  <li className={processing ? 'current' : withdrawal.status === 'COMPLETED' ? 'done' : ''}>
                    <strong>Chuyển khoản</strong>
                    <span>
                      {withdrawal.status === 'COMPLETED'
                        ? formatWithdrawalDate(withdrawal.completedAt)
                        : processing
                          ? 'Chờ xác nhận giao dịch'
                          : 'Sau khi duyệt'}
                    </span>
                  </li>
                </>
              )}
            </ol>
            {pending && (
              <>
                <button className="admin-button" type="button" onClick={() => setDialog('approve')}>
                  Duyệt yêu cầu
                </button>
                <button
                  className="admin-button danger-outline"
                  type="button"
                  onClick={() => {
                    setReason('');
                    setReasonTried(false);
                    setDialog('reject');
                  }}
                >
                  Từ chối yêu cầu
                </button>
              </>
            )}
            {processing && (
              <button className="admin-button" type="button" onClick={() => setDialog('complete')}>
                Xác nhận đã chuyển
              </button>
            )}
            {!pending && !processing && (
              <StatusBadge meta={{ label: withdrawalLabels[withdrawal.status], tone: statusTone[withdrawal.status] }} />
            )}
          </section>
        </aside>
      </div>

      {dialog === 'approve' && (
        <AdminModal
          title="Duyệt yêu cầu rút tiền"
          size="sm"
          onClose={() => !busy && setDialog(null)}
          footer={
            <div className="admin-button-row">
              <button className="admin-button secondary" type="button" disabled={busy} onClick={() => setDialog(null)}>
                Quay lại
              </button>
              <button
                className="admin-button"
                type="button"
                disabled={busy}
                onClick={() =>
                  void run(
                    () => approveWithdrawal(withdrawalId),
                    'Đã duyệt yêu cầu. Tiếp tục chuyển tiền qua ngân hàng.'
                  )
                }
              >
                Duyệt yêu cầu
              </button>
            </div>
          }
        >
          <div className="admin-modal-pad">
            <div className="admin-key-values">
              <div>
                <span>Người nhận</span>
                <strong>{withdrawal.accountHolderName}</strong>
              </div>
              <div>
                <span>Ngân hàng</span>
                <strong>{withdrawal.bankName}</strong>
              </div>
              <div>
                <span>Số tài khoản</span>
                <strong>{data.accountNumber}</strong>
              </div>
              <div>
                <span>Số tiền</span>
                <strong>{formatWithdrawalMoney(withdrawal.amount)}</strong>
              </div>
            </div>
          </div>
        </AdminModal>
      )}

      {dialog === 'reject' && (
        <AdminModal
          title="Từ chối yêu cầu rút tiền"
          size="sm"
          onClose={() => !busy && setDialog(null)}
          footer={
            <div className="admin-button-row">
              <button className="admin-button secondary" type="button" disabled={busy} onClick={() => setDialog(null)}>
                Quay lại
              </button>
              <button
                className="admin-button danger"
                type="button"
                disabled={busy}
                onClick={() => {
                  setReasonTried(true);
                  if (reason.trim())
                    void run(
                      () => rejectWithdrawal(withdrawalId, reason.trim()),
                      'Đã từ chối yêu cầu và trả tiền về ví người rút.'
                    );
                }}
              >
                Xác nhận từ chối
              </button>
            </div>
          }
        >
          <div className="admin-modal-pad">
            <ReasonField
              id="withdrawal-rejection-reason"
              label="Lý do từ chối"
              value={reason}
              minLength={1}
              maxLength={500}
              showError={reasonTried}
              onChange={setReason}
            />
          </div>
        </AdminModal>
      )}

      {dialog === 'complete' && (
        <AdminModal
          title="Xác nhận đã chuyển khoản"
          size="sm"
          onClose={() => !busy && setDialog(null)}
          footer={
            <div className="admin-button-row">
              <button className="admin-button secondary" type="button" disabled={busy} onClick={() => setDialog(null)}>
                Quay lại
              </button>
              <button
                className="admin-button"
                type="button"
                disabled={busy || !reference.trim()}
                onClick={() =>
                  void run(
                    () => completeWithdrawal(withdrawalId, reference.trim()),
                    'Đã ghi nhận chuyển khoản thành công.'
                  )
                }
              >
                Hoàn tất yêu cầu
              </button>
            </div>
          }
        >
          <div className="admin-modal-pad admin-stack-gap">
            <div className="admin-alert admin-alert-warning">
              Hãy kiểm tra giao dịch đã thành công trong ứng dụng ngân hàng trước khi hoàn tất.
            </div>
            <div className="admin-key-values">
              <div>
                <span>Người nhận</span>
                <strong>{withdrawal.accountHolderName}</strong>
              </div>
              <div>
                <span>Số tài khoản</span>
                <strong>{data.accountNumber}</strong>
              </div>
              <div>
                <span>Số tiền</span>
                <strong>{formatWithdrawalMoney(withdrawal.amount)}</strong>
              </div>
            </div>
            <div className="admin-field">
              <label htmlFor="withdrawal-transfer-reference">
                Mã giao dịch ngân hàng <span className="admin-required">*</span>
              </label>
              <input
                id="withdrawal-transfer-reference"
                value={reference}
                maxLength={100}
                autoComplete="off"
                placeholder="Nhập mã từ biên lai giao dịch"
                onChange={(event) => setReference(event.target.value)}
              />
            </div>
          </div>
        </AdminModal>
      )}
    </div>
  );
}

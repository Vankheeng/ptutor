import { useCallback, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { ApiError } from '../../services/httpClient';
import {
  cancelContract,
  getContract,
  getInstallments,
  getWalletBalance,
  payInstallment,
  payInstallmentWithWallet,
  rejectContract,
  renewContract,
  signContract,
  updateContract,
  type Contract,
  type ContractTerms,
  type Installment,
  type WalletBalance
} from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';
import { StudentContractActionBar } from '../../components/student/StudentContractActionBar';
import { StudentContractEditForm } from '../../components/student/StudentContractEditForm';
import { StudentContractOverview } from '../../components/student/StudentContractOverview';
import { StudentContractRenewalForm } from '../../components/student/StudentContractRenewalForm';
import { StudentContractStatusBadge } from '../../components/student/StudentContractStatusBadge';
import { StudentPaymentInstallments } from '../../components/student/StudentPaymentInstallments';
import { StudentPaymentMethodDialog } from '../../components/student/StudentPaymentMethodDialog';
import { Icon } from '../../components/ui/Icon';

export function StudentContractDetailPage() {
  const { contractId = '' } = useParams();
  const location = useLocation();
  const { user } = useAuth();
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState((location.state as { notice?: string } | null)?.notice ?? '');
  const [busy, setBusy] = useState(false);
  const [selectedInstallment, setSelectedInstallment] = useState<Installment | null>(null);
  const [walletBalance, setWalletBalance] = useState<WalletBalance | null>(null);
  const [walletLoading, setWalletLoading] = useState(false);
  const [walletError, setWalletError] = useState('');
  const [paymentError, setPaymentError] = useState('');
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
      setError(caught instanceof ApiError || caught instanceof Error ? caught.message : 'Không thể cập nhật hợp đồng.');
    } finally {
      setBusy(false);
    }
  };

  const saveTerms = async (values: {
    price: number;
    preferredSchedule?: string;
    schedules?: import('../../services/studentApi').ContractSchedule[];
  }) => {
    if (!contract) return;
    await action(() => updateContract(contract.id, values), 'Đã cập nhật hợp đồng.');
  };

  const proposeRenewal = async (terms: ContractTerms) => {
    if (!contract) return;
    await action(() => renewContract(contract.id, terms), 'Đã tạo đề xuất gia hạn.');
  };

  const openPaymentDialog = async (installment: Installment) => {
    setSelectedInstallment(installment);
    setWalletBalance(null);
    setWalletError('');
    setPaymentError('');
    setWalletLoading(true);
    try {
      setWalletBalance(await getWalletBalance());
    } catch (caught) {
      setWalletError(
        caught instanceof ApiError || caught instanceof Error ? caught.message : 'Không thể tải số dư ví.'
      );
    } finally {
      setWalletLoading(false);
    }
  };

  const closePaymentDialog = () => {
    if (busy) return;
    setSelectedInstallment(null);
    setPaymentError('');
  };

  const pay = async () => {
    if (!selectedInstallment) return;
    setBusy(true);
    setPaymentError('');
    try {
      const payment = await payInstallment(contractId, selectedInstallment.id);
      if (!payment.paymentUrl) throw new Error('Không nhận được liên kết thanh toán VNPay.');
      setSelectedInstallment(null);
      window.open(payment.paymentUrl, '_blank', 'noopener,noreferrer');
    } catch (caught) {
      setPaymentError(
        caught instanceof ApiError || caught instanceof Error ? caught.message : 'Không thể tạo thanh toán.'
      );
    } finally {
      setBusy(false);
    }
  };

  const payWithWallet = async () => {
    if (!selectedInstallment) return;
    setBusy(true);
    setPaymentError('');
    try {
      await payInstallmentWithWallet(contractId, selectedInstallment.id);
      setSelectedInstallment(null);
      setNotice('Đã thanh toán kỳ học bằng Ví PTutor.');
      setRevision((value) => value + 1);
    } catch (caught) {
      setPaymentError(
        caught instanceof ApiError || caught instanceof Error ? caught.message : 'Không thể thanh toán bằng Ví.'
      );
    } finally {
      setBusy(false);
    }
  };

  if (!contract) {
    return query.error ? (
      <div className="student-error" role="alert">
        {query.error}
      </div>
    ) : (
      <div className="student-empty">
        <h3>Đang tải hợp đồng...</h3>
      </div>
    );
  }

  const createdByYou = contract.createdByUserId === user?.userId;
  const pendingForSignature = contract.status === 'PENDING' && Boolean(user?.userId) && !createdByYou;
  const pendingCreatedByYou = contract.status === 'PENDING' && Boolean(user?.userId) && createdByYou;
  const tutorName = [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'Gia sư';
  const tutorInitials = tutorName
    .split(/\s+/)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  return (
    <>
      <div className="student-contract-detail-topbar">
        <Link className="student-link" to="/student/contracts">
          <span aria-hidden="true">&larr;</span> Quay lại danh sách hợp đồng
        </Link>
      </div>

      <section className="student-contract-hero">
        <div className="student-contract-hero-main">
          <span className="student-contract-subject-icon" aria-hidden="true">
            <Icon name="contract" size={24} />
          </span>
          <div>
            <span className="student-contract-hero-eyebrow">
              {contract.status === 'PENDING' ? 'YÊU CẦU KÝ HỢP ĐỒNG' : 'HỢP ĐỒNG'}
            </span>
            <h1>{contract.subjectName || 'Môn học'}</h1>
            <span>{contract.gradeName || 'Chưa có lớp'}</span>
          </div>
        </div>
        <div className="student-contract-hero-tutor">
          <span className="student-contract-tutor-avatar" aria-hidden="true">
            {tutorInitials}
          </span>
          <div>
            <strong>{tutorName}</strong>
            <span>
              {contract.status === 'PENDING'
                ? pendingCreatedByYou
                  ? 'Gia sư · Bên cần ký'
                  : 'Gia sư · Người tạo hợp đồng'
                : 'Gia sư'}
            </span>
          </div>
        </div>
        <StudentContractStatusBadge
          status={contract.status}
          label={pendingForSignature ? 'Chờ bạn ký' : pendingCreatedByYou ? 'Chờ gia sư ký' : undefined}
          variant={pendingCreatedByYou ? 'outgoing' : undefined}
        />
      </section>

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
      <StudentContractOverview contract={contract} createdByYou={createdByYou} />
      <StudentContractActionBar
        contract={contract}
        currentUserId={user?.userId}
        busy={busy}
        onSign={() => void action(() => signContract(contract.id), 'Đã ký hợp đồng.')}
        onReject={() => void action(() => rejectContract(contract.id), 'Đã từ chối hợp đồng.')}
        onCancel={() => void action(() => cancelContract(contract.id), 'Đã hủy hợp đồng.')}
      />
      {contract.status === 'PENDING' && (
        <p className="student-contract-pending-note">
          Các khoản thanh toán sẽ khả dụng sau khi hợp đồng được ký và kích hoạt.
        </p>
      )}

      {contract.status === 'PENDING' && createdByYou && (
        <section className="student-panel student-contract-section">
          <h2 className="student-contract-panel-heading">
            <Icon name="contract" size={16} /> Chỉnh sửa điều khoản hợp đồng
          </h2>
          <StudentContractEditForm contract={contract} loading={busy} onSubmit={saveTerms} />
        </section>
      )}
      {contract.status !== 'PENDING' && (
        <div className="student-form-actions student-contract-related-actions">
          <Link className="student-button secondary" to={`/student/contracts/${contract.id}/lessons`}>
            Buổi học và hoàn tiền
          </Link>
        </div>
      )}
      {contract.status === 'ACTIVE' && (
        <StudentPaymentInstallments
          installments={installments}
          loading={busy}
          onPay={(item) => void openPaymentDialog(item)}
        />
      )}
      {contract.status === 'ACTIVE' && (
        <StudentContractRenewalForm contract={contract} loading={busy} onSubmit={proposeRenewal} />
      )}
      {selectedInstallment && (
        <StudentPaymentMethodDialog
          contract={contract}
          installment={selectedInstallment}
          walletBalance={walletBalance}
          walletLoading={walletLoading}
          walletError={walletError}
          busy={busy}
          error={paymentError}
          onClose={closePaymentDialog}
          onPayVnpay={() => void pay()}
          onPayWallet={() => void payWithWallet()}
        />
      )}
    </>
  );
}

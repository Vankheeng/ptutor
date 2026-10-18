import { useCallback, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
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
import { StudentContractActionBar } from '../../components/student/StudentContractActionBar';
import { StudentContractEditForm } from '../../components/student/StudentContractEditForm';
import { StudentContractOverview } from '../../components/student/StudentContractOverview';
import { StudentContractRenewalForm } from '../../components/student/StudentContractRenewalForm';
import { StudentContractStatusBadge } from '../../components/student/StudentContractStatusBadge';
import { StudentPaymentInstallments } from '../../components/student/StudentPaymentInstallments';
import { Icon } from '../../components/ui/Icon';

export function StudentContractDetailPage() {
  const { contractId = '' } = useParams();
  const location = useLocation();
  const { user } = useAuth();
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState((location.state as { notice?: string } | null)?.notice ?? '');
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
        caught instanceof ApiError || caught instanceof Error ? caught.message : 'The contract could not be updated.'
      );
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
    await action(() => updateContract(contract.id, values), 'Contract updated.');
  };

  const proposeRenewal = async (terms: ContractTerms) => {
    if (!contract) return;
    await action(() => renewContract(contract.id, terms), 'Renewal proposal created.');
  };

  const pay = async (installmentId: string) => {
    setBusy(true);
    setError('');
    try {
      const payment = await payInstallment(contractId, installmentId);
      window.open(payment.paymentUrl, '_blank', 'noopener,noreferrer');
    } catch (caught) {
      setError(
        caught instanceof ApiError || caught instanceof Error ? caught.message : 'Could not create the payment.'
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
        <h3>Loading contract...</h3>
      </div>
    );
  }

  const createdByYou = contract.createdByUserId === user?.userId;
  const tutorName = [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'Tutor';
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
          <span aria-hidden="true">&larr;</span> Back to contracts
        </Link>
      </div>

      <section className="student-contract-hero">
        <div className="student-contract-hero-main">
          <span className="student-contract-subject-icon" aria-hidden="true">
            <Icon name="contract" size={24} />
          </span>
          <div>
            <h1>{contract.subjectName || 'Subject'}</h1>
            <span>{contract.gradeName || 'Grade not specified'}</span>
          </div>
        </div>
        <div className="student-contract-hero-tutor">
          <span className="student-contract-tutor-avatar" aria-hidden="true">
            {tutorInitials}
          </span>
          <div>
            <strong>{tutorName}</strong>
            <span>Tutor</span>
          </div>
        </div>
        <StudentContractStatusBadge status={contract.status} />
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
        onSign={() => void action(() => signContract(contract.id), 'Contract signed.')}
        onReject={() => void action(() => rejectContract(contract.id), 'Contract rejected.')}
        onCancel={() => void action(() => cancelContract(contract.id), 'Contract cancelled.')}
      />

      {contract.status === 'PENDING' && createdByYou && (
        <section className="student-panel student-contract-section">
          <h2 className="student-contract-panel-heading">
            <Icon name="contract" size={16} /> Edit contract terms
          </h2>
          <StudentContractEditForm contract={contract} loading={busy} onSubmit={saveTerms} />
        </section>
      )}
      {contract.status === 'ACTIVE' && (
        <StudentPaymentInstallments installments={installments} loading={busy} onPay={(id) => void pay(id)} />
      )}
      {contract.status === 'ACTIVE' && (
        <StudentContractRenewalForm contract={contract} loading={busy} onSubmit={proposeRenewal} />
      )}
    </>
  );
}

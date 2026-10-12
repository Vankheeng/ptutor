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
            : 'The contract could not be updated.'
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
    await action(() => updateContract(contract.id, payload), 'Contract updated.');
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
        caught instanceof ApiError
          ? caught.message
          : caught instanceof Error
            ? caught.message
            : 'Could not create the payment.'
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
        <h3>Loading contract…</h3>
      </div>
    );
  return (
    <>
      <Link className="student-link" to="/student/contracts">
        ← Back to contracts
      </Link>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">CONTRACT DETAILS</span>
          <h1>
            {contract.subjectName} · {contract.gradeName}
          </h1>
          <p>With {[contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'your tutor'}</p>
        </div>
        <span className={`student-status ${contract.status.toLowerCase()}`}>{label(contract.status)}</span>
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
            <small>Price</small>
            <strong>{formatMoney(contract.price)}</strong>
          </span>
          <span>
            <small>Payment period</small>
            <strong>{contract.paymentPeriod}</strong>
          </span>
          <span>
            <small>Lessons</small>
            <strong>{contract.totalLessons}</strong>
          </span>
          <span>
            <small>Schedule</small>
            <strong>{contract.preferredSchedule}</strong>
          </span>
          <span>
            <small>Dates</small>
            <strong>
              {contract.startDate} → {contract.endDate}
            </strong>
          </span>
          <span>
            <small>Created by</small>
            <strong>{contract.createdByUserId === user?.userId ? 'You' : 'Tutor'}</strong>
          </span>
        </div>
        <div className="student-form-actions">
          {contract.status === 'PENDING' && contract.createdByUserId !== user?.userId && (
            <button
              className="student-button"
              disabled={busy}
              type="button"
              onClick={() => void action(() => signContract(contract.id), 'Contract signed.')}
            >
              Sign contract
            </button>
          )}
          {contract.status === 'PENDING' && contract.createdByUserId !== user?.userId && (
            <button
              className="student-button secondary"
              disabled={busy}
              type="button"
              onClick={() => void action(() => rejectContract(contract.id), 'Contract rejected.')}
            >
              Reject
            </button>
          )}
          {contract.status === 'PENDING' && contract.createdByUserId === user?.userId && (
            <button
              className="student-button secondary"
              disabled={busy}
              type="button"
              onClick={() => void action(() => cancelContract(contract.id), 'Contract cancelled.')}
            >
              Cancel contract
            </button>
          )}
        </div>
      </section>
      {contract.status === 'PENDING' && contract.createdByUserId === user?.userId && (
        <section className="student-panel" style={{ marginTop: 15 }}>
          <h2>Edit contract terms</h2>
          <form className="student-form-grid" onSubmit={(event) => void edit(event)}>
            <label>
              Price
              <input name="price" type="number" min="0" defaultValue={contract.price} />
            </label>
            <label>
              Preferred schedule
              <input name="preferredSchedule" defaultValue={contract.preferredSchedule} required />
            </label>
            <label>
              Start date
              <input name="startDate" type="date" defaultValue={contract.startDate} required />
            </label>
            <label>
              End date
              <input name="endDate" type="date" defaultValue={contract.endDate} required />
            </label>
            <div className="student-form-actions">
              <button className="student-button" disabled={busy} type="submit">
                Save changes
              </button>
            </div>
          </form>
        </section>
      )}
      {contract.status === 'ACTIVE' && (
        <section className="student-panel" style={{ marginTop: 15 }}>
          <h2>Payment installments</h2>
          {installments.length ? (
            <div className="student-list">
              {installments.map((item) => (
                <div className="student-list-row" key={item.id}>
                  <div>
                    <h3>Installment {item.sequenceNumber}</h3>
                    <p>
                      {item.dueDate} · {formatMoney(item.amount)}
                    </p>
                  </div>
                  <div>
                    <span className={`student-status ${item.status.toLowerCase()}`}>{label(item.status)}</span>
                    {item.status === 'PENDING' && (
                      <button
                        className="student-button"
                        style={{ marginTop: 8 }}
                        type="button"
                        disabled={busy}
                        onClick={() => void pay(item.id)}
                      >
                        Pay with VNPay
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="student-muted">No installments are available.</p>
          )}
        </section>
      )}
      {contract.status === 'ACTIVE' && (
        <section className="student-panel" style={{ marginTop: 15 }}>
          <h2>Renew this contract</h2>
          <form className="student-form-grid" onSubmit={(event) => void renew(event)}>
            <label>
              Price
              <input name="price" type="number" min="0" defaultValue={contract.price} required />
            </label>
            <label>
              Total lessons
              <input name="totalLessons" type="number" min="1" defaultValue={contract.totalLessons} required />
            </label>
            <label>
              Start date
              <input name="startDate" type="date" required />
            </label>
            <label>
              End date
              <input name="endDate" type="date" required />
            </label>
            <label className="student-field-full">
              Preferred schedule
              <input name="preferredSchedule" defaultValue={contract.preferredSchedule} required />
            </label>
            <div className="student-form-actions">
              <button className="student-button" disabled={busy} type="submit">
                Propose renewal
              </button>
            </div>
          </form>
        </section>
      )}
    </>
  );
}
function label(value: string) {
  return value.toLowerCase().replaceAll('_', ' ');
}
function formatMoney(value: number) {
  return `${new Intl.NumberFormat('en-US').format(value)} VND`;
}

import { useCallback, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getAdminContractTransactions, getAdminHeldTuition, getAdminWallets, getAdminWalletSummary, type AdminContractTransaction, type AdminHeldTuition, type AdminWallet } from '../../services/adminWalletApi';
import { getAdminTransaction, getAdminWalletUserTransactions, type AdminTransaction } from '../../services/adminTransactionApi';
import type { TransactionSource } from '../../services/adminStudentApi';
import { formatCurrency } from '../../utils/formatters';
import { formatDateTime } from './adminFormat';
import { AdminModal, AdminPagination, AdminState, StatusBadge } from './components/AdminUi';
import { transactionMethodLabels, transactionSourceLabels, transactionStatus } from './studentFormat';
import { transactionPurposeLabels, transactionReferenceLabels } from './transactionFormat';

export function AdminWalletsPage() {
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') === 'tuition' ? 'tuition' : 'wallets';
  const search = params.get('search') ?? '';
  const page = Math.max(0, Number(params.get('page')) || 0);
  const [draftSearch, setDraftSearch] = useState(search);
  const [selectedContract, setSelectedContract] = useState<AdminHeldTuition | null>(null);
  const [selectedWallet, setSelectedWallet] = useState<AdminWallet | null>(null);
  const loadSummary = useCallback((signal: AbortSignal) => getAdminWalletSummary(signal), []);
  const { data: summary, error: summaryError, isLoading: summaryLoading } = useResource(loadSummary);
  const loadWallets = useCallback((signal: AbortSignal) => getAdminWallets(search, page, signal), [search, page]);
  const loadTuition = useCallback((signal: AbortSignal) => getAdminHeldTuition(search, page, signal), [search, page]);
  const wallets = useResource(loadWallets);
  const tuition = useResource(loadTuition);
  const data = tab === 'wallets' ? wallets.data : tuition.data;
  const error = tab === 'wallets' ? wallets.error : tuition.error;
  const loading = tab === 'wallets' ? wallets.isLoading : tuition.isLoading;

  const update = (nextTab: 'wallets' | 'tuition', nextSearch = search, nextPage = 0) => {
    const next = new URLSearchParams();
    if (nextTab === 'tuition') next.set('tab', 'tuition');
    if (nextSearch) next.set('search', nextSearch);
    if (nextPage) next.set('page', String(nextPage));
    setParams(next);
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    update(tab, draftSearch.trim());
  };

  return (
    <div className="admin-page admin-wallet-page">
      <header className="admin-wallet-header">
        <h1 className="admin-title">Quản lý ví</h1>
        <nav className="admin-wallet-shortcuts" aria-label="Tác vụ tài chính">
          <Link to="/admin/withdrawals">Yêu cầu rút tiền</Link>
          <Link to="/admin/refund-requests">Yêu cầu hoàn tiền</Link>
          <Link to="/admin/transactions">Lịch sử giao dịch</Link>
        </nav>
      </header>
      {summary ? (
        <section className="admin-wallet-overview" aria-label="Tổng quan ví">
          <Link to="/admin/wallets" className="admin-wallet-metric admin-wallet-metric-primary">
            <span className="admin-wallet-metric-label">Tổng số dư khả dụng</span>
            <strong>{formatCurrency(summary.availableBalance)}</strong>
            <span className="admin-wallet-metric-foot">Trong ví học viên và gia sư</span>
          </Link>
          <Link to="/admin/withdrawals" className="admin-wallet-metric">
            <span className="admin-wallet-metric-label">Đang chờ rút</span>
            <strong>{formatCurrency(summary.pendingWithdrawalBalance)}</strong>
            <span className="admin-wallet-metric-link">Xem yêu cầu</span>
          </Link>
          <Link to="/admin/wallets?tab=tuition" className="admin-wallet-metric">
            <span className="admin-wallet-metric-label">Học phí còn giữ</span>
            <strong>{formatCurrency(summary.heldTuition)}</strong>
            <span className="admin-wallet-metric-link">Xem hợp đồng</span>
          </Link>
          <Link to="/admin/wallets" className="admin-wallet-metric">
            <span className="admin-wallet-metric-label">Ví người dùng</span>
            <strong>{summary.walletCount.toLocaleString('vi-VN')}</strong>
            <span className="admin-wallet-metric-link">Xem danh sách</span>
          </Link>
        </section>
      ) : <AdminState loading={summaryLoading} error={summaryError} />}
      <section className="admin-card admin-wallet-directory">
        <div className="admin-wallet-directory-header">
          <div>
            <h2>{tab === 'wallets' ? 'Ví người dùng' : 'Học phí còn giữ'}</h2>
          </div>
          <div className="admin-wallet-tabs" role="group" aria-label="Loại khoản tiền">
            <button type="button" className={tab === 'wallets' ? 'active' : undefined} aria-pressed={tab === 'wallets'} onClick={() => update('wallets')}>Ví người dùng</button>
            <button type="button" className={tab === 'tuition' ? 'active' : undefined} aria-pressed={tab === 'tuition'} onClick={() => update('tuition')}>Học phí còn giữ <span className="admin-wallet-tab-count">{summary?.heldContractCount ?? 0}</span></button>
          </div>
        </div>
        <form className="admin-transaction-filters" onSubmit={submit}>
          <div className="admin-transaction-search-row">
            <label><span className="admin-sr-only">Tìm kiếm</span>
              <input value={draftSearch} onChange={(event) => setDraftSearch(event.target.value)} placeholder={tab === 'wallets' ? 'Tên hoặc email' : 'Học viên, gia sư hoặc mã hợp đồng'} />
            </label>
            <button className="admin-button" type="submit">Tìm kiếm</button>
            {search && <button className="admin-button ghost" type="button" onClick={() => { setDraftSearch(''); update(tab, ''); }}>Xóa bộ lọc</button>}
          </div>
        </form>
        {!data ? <div className="admin-card-pad"><AdminState loading={loading} error={error} /></div>
          : data.content.length === 0 ? <AdminState empty="Không có dữ liệu phù hợp." />
          : <div className="admin-table-wrap"><table className={`admin-table admin-wallet-table${tab === 'tuition' ? ' admin-wallet-tuition-table' : ''}`}>
            {tab === 'wallets' && wallets.data ? <>
              <thead><tr><th>Người dùng</th><th>Vai trò</th><th>Số dư khả dụng</th><th>Chờ rút</th><th /></tr></thead>
              <tbody>{wallets.data.content.map((item) => <tr key={item.walletId}>
                <td><div className="admin-stack"><strong>{item.userName || item.userEmail}</strong><span>{item.userEmail}</span></div></td>
                <td><span className={`admin-wallet-role admin-wallet-role-${item.userType.toLowerCase()}`}>{item.userType === 'STUDENT' ? 'Học viên' : item.userType === 'TUTOR' ? 'Gia sư' : 'Khác'}</span></td>
                <td className="admin-wallet-amount">{formatCurrency(item.balance)}</td><td className="admin-wallet-amount">{formatCurrency(item.pendingBalance)}</td>
                <td><button className="admin-button small secondary admin-wallet-row-button" type="button" onClick={() => setSelectedWallet(item)}>Giao dịch</button></td>
              </tr>)}</tbody>
            </> : tuition.data && <>
              <thead><tr><th>Học viên</th><th>Gia sư</th><th>Mã hợp đồng</th><th>Đã thu</th><th>Đã hoàn</th><th>Đã trả gia sư</th><th>Còn giữ</th><th>Thanh toán gần nhất</th><th /></tr></thead>
              <tbody>{tuition.data.content.map((item) => <tr key={item.contractId}>
                <td><strong>{item.studentName}</strong></td>
                <td><strong>{item.tutorName}</strong></td>
                <td className="admin-wallet-code" title={item.contractId}>{item.contractId.slice(0, 8)}</td>
                <td className="admin-wallet-amount">{formatCurrency(item.paidAmount)}</td>
                <td>{formatCurrency(item.refundedAmount)}</td><td>{formatCurrency(item.tutorPaidAmount)}</td>
                <td className="admin-wallet-amount">{formatCurrency(item.heldAmount)}</td>
                <td>{item.lastPaidAt ? formatDateTime(item.lastPaidAt) : '—'}</td>
                <td><button className="admin-button small secondary admin-wallet-row-button" type="button" onClick={() => setSelectedContract(item)}>Giao dịch</button></td>
              </tr>)}</tbody>
            </>}
          </table></div>}
        {data && <div className="admin-card-footer"><AdminPagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} unit={tab === 'wallets' ? 'ví' : 'hợp đồng'} onChange={(next) => update(tab, search, next)} /></div>}
      </section>
      {selectedContract && <ContractTransactionsDialog contract={selectedContract} onClose={() => setSelectedContract(null)} />}
      {selectedWallet && <WalletTransactionsDialog wallet={selectedWallet} onClose={() => setSelectedWallet(null)} />}
    </div>
  );
}

function ContractTransactionsDialog({ contract, onClose }: { contract: AdminHeldTuition; onClose: () => void }) {
  const [page, setPage] = useState(0);
  const [selectedTransaction, setSelectedTransaction] = useState<AdminContractTransaction | null>(null);
  const load = useCallback((signal: AbortSignal) => getAdminContractTransactions(contract.contractId, page, signal),
    [contract.contractId, page]);
  const { data, error, isLoading } = useResource(load);
  const labels = {
    TUITION_PAYMENT: 'Học viên đóng học phí',
    TUITION_REFUND: 'Hoàn tiền học phí',
    TUTOR_PAYOUT: 'Trả tiền gia sư',
  };
  return <AdminModal title={selectedTransaction
    ? <span className="admin-contract-dialog-title"><button type="button" className="admin-contract-back-button" aria-label="Quay lại danh sách giao dịch" onClick={() => setSelectedTransaction(null)}>‹</button>Chi tiết giao dịch</span>
    : 'Giao dịch hợp đồng'} size="xl" onClose={onClose}>
    <div className="admin-contract-dialog-content">
      <div className="admin-contract-dialog-context">
        <div><span>Học viên</span><strong>{contract.studentName}</strong></div>
        <div><span>Gia sư</span><strong>{contract.tutorName}</strong></div>
        <div><span>Mã hợp đồng</span><strong className="admin-contract-dialog-code">{contract.contractId}</strong></div>
      </div>
      {selectedTransaction ? <TransactionDetail source={selectedTransaction.type === 'TUITION_PAYMENT' ? 'PAYMENT' : 'WALLET_TRANSACTION'} id={selectedTransaction.id} title={labels[selectedTransaction.type]} />
        : !data ? <div className="admin-modal-pad"><AdminState loading={isLoading} error={error} /></div>
          : data.content.length === 0 ? <div className="admin-modal-pad"><AdminState empty="Hợp đồng chưa có giao dịch." /></div>
            : <>
              <div className="admin-contract-transaction-list" aria-label="Danh sách giao dịch của hợp đồng">
                {data.content.map((item) => <button key={item.id} className="admin-contract-transaction-item" type="button" onClick={() => setSelectedTransaction(item)}>
                  <span className={`admin-contract-transaction-icon admin-contract-transaction-icon-${item.type.toLowerCase()}`} aria-hidden="true">
                    {item.type === 'TUITION_PAYMENT' ? '₫' : item.type === 'TUITION_REFUND' ? '↩' : '✓'}
                  </span>
                  <span className="admin-contract-transaction-main"><strong>{labels[item.type]}</strong><small>{item.userName}</small></span>
                  <span className="admin-contract-transaction-date">{formatDateTime(item.occurredAt)}</span>
                  <span className="admin-contract-transaction-right"><strong>{formatCurrency(item.amount)}</strong><small>{item.status === 'COMPLETED' || item.status === 'PAID' ? 'Hoàn tất' : item.status === 'REFUNDED' ? 'Đã hoàn' : item.status}</small></span>
                </button>)}
              </div>
              <div className="admin-card-footer"><AdminPagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} unit="giao dịch" onChange={setPage} /></div>
            </>}
    </div>
  </AdminModal>;
}

function WalletTransactionsDialog({ wallet, onClose }: { wallet: AdminWallet; onClose: () => void }) {
  const [page, setPage] = useState(0);
  const [selectedTransaction, setSelectedTransaction] = useState<AdminTransaction | null>(null);
  const load = useCallback((signal: AbortSignal) => getAdminWalletUserTransactions(wallet.userId, page, signal),
    [wallet.userId, page]);
  const { data, error, isLoading } = useResource(load);
  return <AdminModal title={selectedTransaction
    ? <span className="admin-contract-dialog-title"><button type="button" className="admin-contract-back-button" aria-label="Quay lại danh sách giao dịch" onClick={() => setSelectedTransaction(null)}>‹</button>Chi tiết giao dịch</span>
    : 'Giao dịch ví người dùng'} size="xl" onClose={onClose}>
    <div className="admin-contract-dialog-content">
      <div className="admin-contract-dialog-context">
        <div><span>Người dùng</span><strong>{wallet.userName || wallet.userEmail}</strong><small>{wallet.userEmail}</small></div>
        <div><span>Số dư khả dụng</span><strong>{formatCurrency(wallet.balance)}</strong></div>
        <div><span>Đang chờ rút</span><strong>{formatCurrency(wallet.pendingBalance)}</strong></div>
      </div>
      {selectedTransaction ? <TransactionDetail source="WALLET_TRANSACTION" id={selectedTransaction.transaction.id}
          title={transactionPurposeLabels[selectedTransaction.purpose] ?? selectedTransaction.purpose} />
        : !data ? <div className="admin-modal-pad"><AdminState loading={isLoading} error={error} /></div>
          : data.content.length === 0 ? <div className="admin-modal-pad"><AdminState empty="Ví chưa có giao dịch." /></div>
            : <>
              <div className="admin-contract-transaction-list" aria-label="Danh sách giao dịch của ví người dùng">
                {data.content.map((item) => <button key={item.transaction.id} className="admin-contract-transaction-item" type="button" onClick={() => setSelectedTransaction(item)}>
                  <span className={`admin-contract-transaction-icon ${item.transaction.type === 'CREDIT' ? 'admin-wallet-credit-icon' : 'admin-wallet-debit-icon'}`} aria-hidden="true">
                    {item.transaction.type === 'CREDIT' ? '+' : '−'}
                  </span>
                  <span className="admin-contract-transaction-main"><strong>{transactionPurposeLabels[item.purpose] ?? item.purpose}</strong><small>{item.transaction.description || wallet.userEmail}</small></span>
                  <span className="admin-contract-transaction-date">{formatDateTime(item.transaction.occurredAt)}</span>
                  <span className="admin-contract-transaction-right"><strong>{formatCurrency(item.transaction.amount)}</strong><small>{item.transaction.status === 'COMPLETED' ? 'Hoàn tất' : item.transaction.status === 'PENDING' ? 'Đang xử lý' : item.transaction.status}</small></span>
                </button>)}
              </div>
              <div className="admin-card-footer"><AdminPagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} unit="giao dịch" onChange={setPage} /></div>
            </>}
    </div>
  </AdminModal>;
}

function TransactionDetail({ source, id, title }: { source: TransactionSource; id: string; title: string }) {
  const load = useCallback((signal: AbortSignal) => getAdminTransaction(source, id, signal), [source, id]);
  const { data, error, isLoading } = useResource(load);
  return <div className="admin-contract-detail">
    {!data ? <AdminState loading={isLoading} error={error} /> : <>
      <div className="admin-contract-detail-hero">
        <div><span>{title}</span><strong>{formatCurrency(data.transaction.amount)}</strong></div>
        <StatusBadge meta={transactionStatus(data.transaction.status)} />
      </div>
      <dl className="admin-contract-detail-facts">
        <div><dt>Thời gian</dt><dd>{formatDateTime(data.transaction.occurredAt)}</dd></div>
        <div><dt>Người liên quan</dt><dd>{data.userName || data.userEmail}</dd></div>
        <div><dt>Email</dt><dd>{data.userEmail}</dd></div>
        <div><dt>Phương thức</dt><dd>{transactionMethodLabels[data.transaction.method] ?? data.transaction.method}</dd></div>
        <div><dt>Nguồn giao dịch</dt><dd>{transactionSourceLabels[data.transaction.source]}</dd></div>
        <div><dt>Mã giao dịch</dt><dd className="admin-contract-dialog-code">{data.transaction.id}</dd></div>
        {data.transactionCode && <div><dt>Mã thanh toán</dt><dd className="admin-contract-dialog-code">{data.transactionCode}</dd></div>}
        {data.transaction.referenceType && <div><dt>Loại tham chiếu</dt><dd>{transactionReferenceLabels[data.transaction.referenceType] ?? data.transaction.referenceType}</dd></div>}
        {data.transaction.referenceId && <div><dt>Mã tham chiếu</dt><dd className="admin-contract-dialog-code">{data.transaction.referenceId}</dd></div>}
        {data.transaction.description && <div className="admin-contract-detail-wide"><dt>Ghi chú</dt><dd>{data.transaction.description}</dd></div>}
      </dl>
    </>}
  </div>;
}

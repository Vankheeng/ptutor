interface WalletTopUpFormProps {
  amount: string;
  onAmountChange: (amount: string) => void;
  onSubmit: () => void;
}

export function WalletTopUpForm({ amount, onAmountChange, onSubmit }: WalletTopUpFormProps) {
  return (
    <section className="student-panel">
      <h2>Top up wallet</h2>
      <div className="student-form-grid">
        <label>
          Amount (VND)
          <input
            type="number"
            min="10000"
            step="1"
            value={amount}
            onChange={(event) => onAmountChange(event.target.value)}
            placeholder="100000"
          />
        </label>
        <div className="student-form-actions">
          <button className="student-button" type="button" disabled={!amount} onClick={onSubmit}>
            Open VNPay
          </button>
        </div>
      </div>
    </section>
  );
}

import { useEffect, useState } from 'react';
import { VietQR } from 'vietqr';

interface Props {
  bankCode: string;
  accountNumber: string;
  accountHolderName: string;
  amount: number;
  withdrawalId: string;
}

const bankAliases: Record<string, string> = { CTG: 'ICB' };

export function WithdrawalTransferQr({ bankCode, accountNumber, accountHolderName, amount, withdrawalId }: Props) {
  const [result, setResult] = useState<{ key: string; qr: string | null; error: string | null } | null>(null);
  const memo = `PTUTOR RUT ${withdrawalId.replaceAll('-', '').slice(0, 12).toUpperCase()}`;
  const key = `${bankCode}:${accountNumber}:${accountHolderName}:${amount}:${memo}`;
  const invalid = !Number.isSafeInteger(amount) || amount <= 0 || !accountNumber;
  const qr = result?.key === key ? result.qr : null;
  const error = invalid
    ? 'Không thể tạo QR cho số tiền hoặc tài khoản này. Hãy dùng thông tin chuyển khoản hiển thị ở trên.'
    : result?.key === key
      ? result.error
      : null;

  useEffect(() => {
    let active = true;
    if (invalid) return;
    void new VietQR()
      .genQRCodeBase64({
        bank: bankAliases[bankCode.toUpperCase()] ?? bankCode,
        accountName: accountHolderName,
        accountNumber,
        amount: String(amount),
        memo,
        media: 'png'
      })
      .then((result) => {
        if (!active) return;
        if (result.data.code !== '00' || !result.data.data?.qrDataURL) {
          setResult({
            key,
            qr: null,
            error: 'Không thể tạo QR cho ngân hàng hoặc tài khoản này. Hãy dùng thông tin chuyển khoản hiển thị ở trên.'
          });
          return;
        }
        setResult({ key, qr: result.data.data.qrDataURL, error: null });
      })
      .catch(() => {
        if (active)
          setResult({ key, qr: null, error: 'Không thể tạo mã QR. Hãy dùng thông tin chuyển khoản hiển thị ở trên.' });
      });
    return () => {
      active = false;
    };
  }, [bankCode, accountNumber, accountHolderName, amount, memo, invalid, key]);

  return (
    <div className="admin-withdrawal-qr">
      <div className="admin-withdrawal-qr-image">
        {qr ? (
          <img src={qr} width={220} height={220} alt={`QR chuyển khoản tới ${accountHolderName}`} />
        ) : error ? (
          <p role="alert">{error}</p>
        ) : (
          <p>Đang tạo mã QR…</p>
        )}
      </div>
      <div className="admin-withdrawal-qr-details">
        <span className="admin-badge admin-badge-brand">VietQR</span>
        <strong>Quét để điền thông tin</strong>
        <div>
          <span>Nội dung chuyển khoản</span>
          <b>{memo}</b>
        </div>
        {qr && (
          <a className="admin-button secondary" href={qr} download={`ptutor-rut-tien-${withdrawalId}.png`}>
            Tải mã QR
          </a>
        )}
      </div>
    </div>
  );
}

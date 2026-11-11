export function WalletPolicy() {
  return (
    <>
      <section className="wallet-policy">
        <h2>
          <span aria-hidden="true">⚖</span> QUY ĐỊNH &amp; CHÍNH SÁCH BẢO CHỨNG TÀI CHÍNH PTUTOR
        </h2>
        <div className="wallet-policy-grid">
          <article className="wallet-policy-escrow">
            <span aria-hidden="true">◷</span>
            <div>
              <b>Cơ chế Bảo chứng Escrow 72 Giờ</b>
              <p>
                Sau khi buổi học kết thúc và không có khiếu nại, khoản tiền được giải ngân theo quy định. Giao dịch được
                bảo vệ trong hệ thống PTutor Trust.
              </p>
            </div>
          </article>
          <article className="wallet-policy-security">
            <span aria-hidden="true">⬟</span>
            <div>
              <b>Quy tắc Bảo toàn Quyền lợi Tài khoản</b>
              <p>
                Lệnh rút tiền cần xác thực OTP và được kiểm tra trước khi giải ngân về tài khoản ngân hàng đã liên kết.
              </p>
            </div>
          </article>
        </div>
      </section>
      <footer className="wallet-trust-note">
        <b>
          <span aria-hidden="true">♢</span> PTutor – Cam kết tin cậy và tuân thủ tài chính
        </b>
        <p>
          Hệ thống ví PTutor ghi nhận các giao dịch nạp, thanh toán và rút tiền. Yêu cầu rút tiền cần qua bước xác thực
          và quy trình đối soát để bảo vệ tài khoản của bạn.
        </p>
      </footer>
    </>
  );
}

import type { ContractSchedule, PaymentPeriod } from '../../../services/studentApi';

export interface ContractPdfDetails {
  studentName: string;
  studentEmail: string;
  tutorName: string;
  tutorEmail: string;
  studentAddress?: string | null;
  studentPhone?: string | null;
  tutorAddress?: string | null;
  tutorPhone?: string | null;
  subject: string;
  grade: string | null | undefined;
  teachingMode: string;
  studentSigned?: boolean;
  tutorSigned?: boolean;
  studentSignedAt?: string | null;
  tutorSignedAt?: string | null;
  contractStatus?: string | null;
  cancelledAt?: string | null;
}

interface Props extends ContractPdfDetails {
  id?: string;
  className?: string;
  label?: string;
  price: number | string;
  paymentPeriod: PaymentPeriod;
  startDate: string;
  endDate: string;
  schedules: ContractSchedule[];
}

export function ContractPdfLink(props: Props) {
  const openDocument = () => {
    const blob = new Blob([createContractDocument(props)], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const popup = window.open(url, '_blank');
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    if (!popup) return;
  };

  return (
    <a
      id={props.id}
      className={props.className}
      href="#contract-pdf"
      onClick={(event) => {
        event.preventDefault();
        openDocument();
      }}
    >
      {props.label ?? 'Chi tiết hợp đồng PDF'}
    </a>
  );
}

function createContractDocument(props: Props) {
  const terms = [
    '\u0048ai b\u00ean \u0111\u1ed3ng \u00fd v\u1edbi c\u00e1c \u0111i\u1ec1u kho\u1ea3n \u0111\u00e3 ghi trong h\u1ee3p \u0111\u1ed3ng.',
    '\u004egh\u1ec9 h\u1ecdc ph\u1ea3i th\u00f4ng b\u00e1o tr\u01b0\u1edbc; hai b\u00ean c\u00f3 th\u1ec3 \u0111\u1ed5i l\u1ecbch ho\u1eb7c trao \u0111\u1ed5i v\u1ec1 ho\u00e0n ti\u1ec1n.',
    '\u0043\u00e1c bu\u1ed5i h\u1ecdc c\u1ea7n \u0111\u01b0\u1ee3c thanh to\u00e1n tr\u01b0\u1edbc khi b\u1eaft \u0111\u1ea7u. V\u1edbi chu k\u1ef3 thanh to\u00e1n theo th\u00e1ng, h\u1ecdc ph\u00ed ph\u1ea3i \u0111\u01b0\u1ee3c thanh to\u00e1n tr\u01b0\u1edbc khi bu\u1ed5i h\u1ecdc \u0111\u1ea7u ti\u00ean c\u1ee7a th\u00e1ng \u0111\u00f3 b\u1eaft \u0111\u1ea7u.',
    '\u0048\u1ecdc ph\u00ed c\u1ee7a bu\u1ed5i \u0111\u00e3 \u0111\u01b0\u1ee3c x\u00e1c nh\u1eadn tham gia kh\u00f4ng \u0111\u01b0\u1ee3c ho\u00e0n l\u1ea1i.',
    '\u0048ai b\u00ean c\u00f3 th\u1ec3 khi\u1ebfu n\u1ea1i theo quy tr\u00ecnh c\u1ee7a PTutor.',
    '\u0048ai b\u00ean c\u00f3 th\u1ec3 y\u00eau c\u1ea7u d\u1eebng h\u1ee3p \u0111\u1ed3ng khi \u0111\u00e3 trao \u0111\u1ed5i v\u00e0 th\u1ed1ng nh\u1ea5t.',
    '\u0042u\u1ed5i h\u1ecdc b\u1ecb \u0111\u00e1nh d\u1ea5u \u0111\u00e3 d\u1ea1y nh\u01b0ng th\u1ef1c t\u1ebf kh\u00f4ng di\u1ec5n ra c\u00f3 th\u1ec3 \u0111\u01b0\u1ee3c y\u00eau c\u1ea7u ho\u00e0n ti\u1ec1n.'
  ];
  const termsHtml = terms.map((term) => '<li>' + escapeHtml(term) + '</li>').join('');
  const contractRows = [
    detailRow('\u004d\u00f4n h\u1ecdc', props.subject),
    detailRow('\u004c\u1edbp', props.grade),
    detailRow('\u0048\u00ecnh th\u1ee9c', props.teachingMode),
    detailRow('\u0047i\u00e1 m\u1ed7i bu\u1ed5i h\u1ecdc', formatMoney(props.price)),
    detailRow('\u0043hu k\u1ef3 thanh to\u00e1n', paymentLabel(props.paymentPeriod)),
    detailRow('\u0054h\u1eddi gian', props.startDate + ' \u0111\u1ebfn ' + props.endDate),
    detailRow('\u004c\u1ecbch h\u1ecdc', formatSchedules(props.schedules)),
    detailRow('\u004e\u0067\u00e0y k\u00fd', signedDateSummary(props)),
    props.contractStatus === 'CANCELLED'
      ? detailRow('\u0054r\u1ea1ng th\u00e1i', '\u0110\u00e3 h\u1ee7y l\u00fac ' + formatDateTime(props.cancelledAt))
      : ''
  ].join('');
  const studentContact = contactInfo(props.studentName, props.studentEmail, props.studentPhone, props.studentAddress);
  const tutorContact = contactInfo(props.tutorName, props.tutorEmail, props.tutorPhone, props.tutorAddress);

  return [
    '<!doctype html><html lang="vi"><head><meta charset="utf-8"><title>Chi ti\u1ebft h\u1ee3p \u0111\u1ed3ng</title>',
    '<style>@page{size:A4;margin:20mm}body{font-family:"Times New Roman",Times,serif;max-width:820px;margin:35px auto;padding:0 28px;color:#111;line-height:1.6;font-size:15px}.national{text-align:center;font-weight:700}.national small{display:block;font-style:italic}.title{text-align:center;text-transform:uppercase;font-size:22px;margin:28px 0}h2{font-size:17px;margin-top:22px;text-transform:uppercase}.table{width:100%;border-collapse:collapse}.table td{border:1px solid #777;padding:7px;vertical-align:top}.table td:first-child{width:28%;font-weight:700}.signatures{display:grid;grid-template-columns:1fr 1fr;gap:50px;margin-top:45px;text-align:center}.signed{color:#16723b;font-weight:700}.print{position:fixed;right:24px;top:18px;padding:8px 14px;background:#6935db;color:#fff;border:1px solid #6935db;border-radius:7px;font-family:Arial,Segoe UI,sans-serif;font-size:14px;font-weight:700;cursor:pointer}@media print{.print{display:none}body{margin:0}}</style></head><body>',
    '<button class="print" onclick="window.print()">In / L\u01b0u PDF</button>',
    '<div class="national">C\u1ed8NG H\u00d2A X\u00c3 H\u1ed8I CH\u1ee6 NGH\u0128A VI\u1ec6T NAM<small>\u0110\u1ed9c l\u1eadp - T\u1ef1 do - H\u1ea1nh ph\u00fac</small></div>',
    '<h1 class="title">H\u1ee2P \u0110\u1ed2NG D\u1ea0Y K\u00c8M</h1>',
    '<p>H\u00f4m nay, hai b\u00ean c\u00f9ng th\u1ed1ng nh\u1ea5t l\u1eadp v\u00e0 th\u1ef1c hi\u1ec7n h\u1ee3p \u0111\u1ed3ng v\u1edbi c\u00e1c th\u00f4ng tin sau:</p>',
    '<h2>1. Th\u00f4ng tin c\u00e1c b\u00ean</h2><table class="table"><tr><td>H\u1ecdc vi\u00ean</td><td>' +
      studentContact +
      '</td></tr><tr><td>Gia s\u01b0</td><td>' +
      tutorContact +
      '</td></tr></table>',
    '<h2>Th\u00f4ng tin PTutor</h2><p>PTutor - N\u1ec1n t\u1ea3ng k\u1ebft n\u1ed1i h\u1ecdc vi\u00ean v\u00e0 gia s\u01b0.</p><p>H\u1ee3p \u0111\u1ed3ng \u0111\u01b0\u1ee3c t\u1ea1o v\u00e0 l\u01b0u tr\u00ean h\u1ec7 th\u1ed1ng PTutor.</p>',
    '<h2>2. Th\u00f4ng tin h\u1ee3p \u0111\u1ed3ng</h2><table class="table">' + contractRows + '</table>',
    '<h2>3. \u0110i\u1ec1u kho\u1ea3n h\u1ee3p \u0111\u1ed3ng</h2><ol>' + termsHtml + '</ol>',
    '<div class="signatures"><div><b>H\u1eccC VI\u00caN</b><p>' +
      escapeHtml(props.studentName) +
      '</p><p class="' +
      (props.studentSigned ? 'signed' : '') +
      '">' +
      signatureStatus(props.studentSigned) +
      '</p>' +
      signatureDate(props.studentSigned, props.studentSignedAt) +
      '</div><div><b>GIA S\u01af</b><p>' +
      escapeHtml(props.tutorName) +
      '</p><p class="' +
      (props.tutorSigned ? 'signed' : '') +
      '">' +
      signatureStatus(props.tutorSigned) +
      '</p>' +
      signatureDate(props.tutorSigned, props.tutorSignedAt) +
      '</div></div>',
    '</body></html>'
  ].join('');
}

function contactInfo(name: string, email: string, phone?: string | null, address?: string | null) {
  const details = [
    ['Email', email],
    ['\u0110i\u1ec7n tho\u1ea1i', phone],
    ['\u0110\u1ecba ch\u1ec9', address]
  ].filter(([, value]) => Boolean(value && value.trim()));
  return (
    '<b>' +
    escapeHtml(name) +
    '</b>' +
    details.map(([label, value]) => '<br>' + label + ': ' + escapeHtml(value as string)).join('')
  );
}

function detailRow(label: string, value: string | null | undefined) {
  return value && value.trim() ? '<tr><td>' + label + '</td><td>' + escapeHtml(value) + '</td></tr>' : '';
}

function formatMoney(value: number | string) {
  const amount = Number(value);
  return (Number.isFinite(amount) ? amount.toLocaleString('vi-VN') : escapeHtml(String(value))) + ' VN\u0110';
}

function escapeHtml(value: string) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function formatSchedules(schedules: ContractSchedule[]) {
  if (!schedules.length) return '\u0043h\u01b0a c\u00f3 l\u1ecbch';
  const days = [
    '',
    '\u0054h\u1ee9 Hai',
    '\u0054h\u1ee9 Ba',
    '\u0054h\u1ee9 T\u01b0',
    '\u0054h\u1ee9 N\u0103m',
    '\u0054h\u1ee9 S\u00e1u',
    '\u0054h\u1ee9 B\u1ea3y',
    '\u0043h\u1ee7 Nh\u1eadt'
  ];
  return schedules
    .map((schedule) => (days[schedule.dayOfWeek] || '') + ' ' + schedule.startTime + '-' + schedule.endTime)
    .join('; ');
}

function paymentLabel(value: PaymentPeriod) {
  return value === 'PACKAGE' ? '\u0054heo g\u00f3i' : '\u0048\u00e0ng th\u00e1ng';
}

function signatureStatus(signed?: boolean) {
  return signed ? '\u0110\u00e3 k\u00fd' : '\u0043h\u01b0a k\u00fd';
}

function signatureDate(signed: boolean | undefined, value: string | null | undefined) {
  return signed && value ? '<p>Ng\u00e0y k\u00fd: ' + escapeHtml(formatDateTime(value)) + '</p>' : '';
}

function signedDateSummary(props: ContractPdfDetails) {
  const values = [
    props.studentSigned && props.studentSignedAt ? 'Học viên: ' + formatDateTime(props.studentSignedAt) : '',
    props.tutorSigned && props.tutorSignedAt ? 'Gia sư: ' + formatDateTime(props.tutorSignedAt) : ''
  ].filter(Boolean);
  return values.join('; ');
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return 'không xác định';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(date);
}

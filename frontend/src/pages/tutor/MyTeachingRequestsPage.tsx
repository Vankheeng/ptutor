import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useMyTeachingRequests } from '../../hooks/useMyTeachingRequests';
import {
  cancelMyTeachingRequest,
  createMyTeachingRequest,
  payTeachingRequestByWallet,
  startTeachingRequestVnPay,
  teachingRequestWalletErrorMessage,
  updateMyTeachingRequest,
  updateMyTeachingRequestStatus,
  TEACHING_REQUEST_FEE_PER_STUDENT,
  type TeachingRequestInput
} from '../../services/myTeachingRequestApi';
import { getGrades } from '../../services/gradeApi';
import { getAllDistricts, getDistricts, getProvinces } from '../../services/locationApi';
import { getSubjects } from '../../services/subjectApi';
import type { District, Grade, Province, Subject, TeachingRequest } from '../../types/api';
import type { Availability } from '../../services/studentApi';
import { StudentAvailabilityEditor } from '../../components/student/StudentAvailabilityEditor';
import { TeachingSubjectsField } from '../common-user/components/TeachingSubjectsField';
import { MultiSelectDropdown } from '../common-user/components/MultiSelectDropdown';
import { RequestModeField } from '../common-user/components/RequestModeField';
import { ApiError } from '../../services/httpClient';
import './my-teaching-requests.css';
import '../student/student.css';

export function MyTeachingRequestsPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const initialEditRequest = (location.state as { editRequest?: TeachingRequest } | null)?.editRequest ?? null;
  const initialEditRequestRef = useRef(initialEditRequest);
  const { data, error, isLoading, reload } = useMyTeachingRequests();
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [grades, setGrades] = useState<Grade[]>([]);
  const [provinces, setProvinces] = useState<Province[]>([]);
  const [districts, setDistricts] = useState<District[]>([]);
  const [provinceId, setProvinceId] = useState('');
  const [districtIds, setDistrictIds] = useState<string[]>(
    () => initialEditRequest?.districts.map((district) => district.id) ?? []
  );
  const [detailAddress, setDetailAddress] = useState(() => initialEditRequest?.detailAddress ?? '');
  const [availabilities, setAvailabilities] = useState<Availability[]>(() =>
    initialEditRequest?.availabilities?.length
      ? initialEditRequest.availabilities
      : [{ dayOfWeek: 0, startTime: '', endTime: '' }]
  );
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('ALL');
  const [sort, setSort] = useState<'DESC' | 'ASC'>('DESC');
  const [pagination, setPagination] = useState({ filterKey: '', page: 1 });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [formOpen, setFormOpen] = useState(Boolean(initialEditRequest));
  const [subjectIds, setSubjectIds] = useState<string[]>(() =>
    initialEditRequest ? [initialEditRequest.subjectId ?? 'OTHER'] : []
  );
  const [gradeIds, setGradeIds] = useState<string[]>(() => initialEditRequest?.grades.map((grade) => grade.id) ?? []);
  const [customSubjectName, setCustomSubjectName] = useState(() => initialEditRequest?.customSubjectName ?? '');
  const [quantity, setQuantity] = useState(() => String(initialEditRequest?.quantity ?? 1));
  const [teachingMode, setTeachingMode] = useState<'ONLINE' | 'OFFLINE'>(
    () => initialEditRequest?.teachingMode ?? 'ONLINE'
  );
  const [editing, setEditing] = useState<TeachingRequest | null>(initialEditRequest);
  const [menuId, setMenuId] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    void Promise.all([getSubjects(), getGrades(), getProvinces()])
      .then(([subjectList, gradeList, provinceList]) => {
        setSubjects(subjectList);
        setGrades(gradeList);
        setProvinces(provinceList);
      })
      .catch(() => setMessage('Không tải được danh mục môn học và khối lớp.'));
  }, []);

  useEffect(() => {
    if (!provinceId) return;
    let active = true;
    getDistricts(provinceId)
      .then((items) => {
        if (active) setDistricts(items);
      })
      .catch(() => {
        if (active) setMessage('Không tải được danh sách quận, huyện.');
      });
    return () => {
      active = false;
    };
  }, [provinceId]);

  useEffect(() => {
    const request = initialEditRequestRef.current;
    if (!request?.districts.length) return;
    void getAllDistricts().then((all) => {
      const district = all.find((item) => item.id === request.districts[0].id);
      if (district) setProvinceId(district.provinceId);
    });
  }, []);

  useEffect(() => {
    if (initialEditRequest) navigate(location.pathname, { replace: true, state: null });
  }, [initialEditRequest, location.pathname, navigate]);

  useEffect(() => {
    if (!menuId) return;
    const onPointerDown = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setMenuId(null);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMenuId(null);
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [menuId]);

  const requests = useMemo(
    () =>
      (data ?? [])
        .filter((request) => status === 'ALL' || request.status === status)
        .filter((request) =>
          `${request.title ?? ''} ${request.subjectName ?? ''} ${request.customSubjectName ?? ''} ${request.description ?? ''}`
            .toLocaleLowerCase('vi')
            .includes(search.toLocaleLowerCase('vi'))
        )
        .sort((a, b) =>
          sort === 'DESC' ? b.createdAt.localeCompare(a.createdAt) : a.createdAt.localeCompare(b.createdAt)
        ),
    [data, search, sort, status]
  );
  const pageSize = 9;
  const pageCount = Math.max(1, Math.ceil(requests.length / pageSize));
  const filterKey = JSON.stringify([search, status, sort]);
  const activePage = Math.min(pagination.filterKey === filterKey ? pagination.page : 1, pageCount);
  const visibleRequests = requests.slice((activePage - 1) * pageSize, activePage * pageSize);
  const firstPageButton = Math.max(1, activePage - 2);
  const lastPageButton = Math.min(pageCount, activePage + 2);
  const goToPage = (nextPage: number) => setPagination({ filterKey, page: nextPage });
  const learnerCount = Math.max(0, Number(quantity) || 0);
  const amountDue = subjectIds.length ? learnerCount * TEACHING_REQUEST_FEE_PER_STUDENT : 0;

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setBusy(true);
    setMessage('');
    const form = new FormData(event.currentTarget);
    const selectedSubjects = subjectIds.filter((id) => id !== 'OTHER');
    if (subjectIds.includes('OTHER') && !customSubjectName.trim()) {
      setBusy(false);
      setMessage('Vui lòng nhập tên môn học khác.');
      return;
    }
    const requestSubjects = [
      ...selectedSubjects.map((id) => ({ subjectId: id })),
      ...(subjectIds.includes('OTHER') ? [{ customSubjectName: customSubjectName.trim() }] : [])
    ];
    const values: Omit<TeachingRequestInput, 'subjectId' | 'customSubjectName'> = {
      gradeIds,
      title: String(form.get('title') ?? '').trim(),
      quantity: Number(form.get('quantity')),
      expectedPrice: Number(form.get('expectedPrice')),
      teachingMode,
      note: String(form.get('note') ?? '').trim() || undefined,
      description: String(form.get('description') ?? '').trim() || undefined,
      districtIds,
      detailAddress: String(form.get('detailAddress') ?? '').trim() || undefined,
      availabilities: availabilities.filter((slot) => slot.dayOfWeek && slot.startTime && slot.endTime)
    };
    if (requestSubjects.length === 0) {
      setBusy(false);
      setMessage('Vui lòng chọn ít nhất một môn có thể dạy.');
      return;
    }
    if (values.gradeIds.length === 0) {
      setBusy(false);
      setMessage('Vui lòng chọn ít nhất một khối lớp.');
      return;
    }
    const submitter = (event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null;
    const submitAction = submitter?.value ?? 'draft';
    const payImmediately = !editing && submitAction === 'pay';
    const paymentMethod = payImmediately ? String(form.get('paymentMethod')) : '';
    const paymentTab = paymentMethod === 'vnpay' ? window.open('about:blank', '_blank') : null;
    let createdCount = 0;
    let paidCount = 0;
    let paymentStage = false;
    try {
      const savedRequests: TeachingRequest[] = [];
      if (editing) {
        savedRequests.push(await updateMyTeachingRequest(editing.id, { ...values, ...requestSubjects[0] }));
      } else {
        for (const subject of requestSubjects) {
          savedRequests.push(await createMyTeachingRequest({ ...values, ...subject }));
        }
      }
      createdCount = savedRequests.length;
      if (!editing) {
        setFormOpen(false);
        setEditing(null);
        reload();
      }
      if (!editing && paymentMethod === 'wallet') {
        paymentStage = true;
        for (const request of savedRequests) {
          await payTeachingRequestByWallet(request.id);
          paidCount += 1;
        }
        setMessage(`Đã thanh toán bằng ví và đăng ${paidCount} yêu cầu thành công.`);
      } else if (!editing && paymentMethod === 'vnpay') {
        paymentStage = true;
        const payment = await startTeachingRequestVnPay(savedRequests[0].id);
        if (!payment.paymentUrl) throw new Error('Không nhận được liên kết thanh toán VNPay.');
        if (paymentTab) paymentTab.location.href = payment.paymentUrl;
        else window.location.assign(payment.paymentUrl);
        setMessage('Đã mở trang thanh toán VNPay. Yêu cầu vẫn là bản nháp cho đến khi thanh toán thành công.');
      } else {
        setMessage(
          editing
            ? 'Đã cập nhật yêu cầu dạy.'
            : `Đã lưu ${savedRequests.length} bản nháp. Bạn có thể thanh toán sau trong trang chi tiết.`
        );
      }
      setFormOpen(false);
      setEditing(null);
      reload();
    } catch (caught) {
      const errorDetails =
        caught instanceof ApiError ? ` (${caught.status}${caught.code ? ` · ${caught.code}` : ''})` : '';
      paymentTab?.close();
      if (paymentStage && paymentMethod === 'wallet') {
        window.alert(teachingRequestWalletErrorMessage(caught));
      }
      setMessage(
        createdCount > 0 && paymentStage
          ? paymentMethod === 'wallet'
            ? `Đã tạo ${createdCount} yêu cầu; thanh toán ví thành công ${paidCount}/${createdCount}. Các yêu cầu chưa thanh toán vẫn là bản nháp${errorDetails}.`
            : `Đã lưu ${createdCount} yêu cầu thành bản nháp, nhưng chưa mở được thanh toán VNPay${errorDetails}.`
          : createdCount > 0
            ? `Đã tạo ${createdCount} yêu cầu ở trạng thái bản nháp${errorDetails}.`
            : caught instanceof Error
              ? caught.message
              : 'Không thể lưu yêu cầu dạy.'
      );
    } finally {
      setBusy(false);
    }
  };

  const editRequest = (request: TeachingRequest) => {
    setEditing(request);
    setDistricts([]);
    setSubjectIds([request.subjectId ?? 'OTHER']);
    setQuantity(String(request.quantity ?? 1));
    setGradeIds(request.grades.map((grade) => grade.id));
    setCustomSubjectName(request.customSubjectName ?? '');
    setTeachingMode(request.teachingMode);
    setDistrictIds(request.districts.map((district) => district.id));
    setDetailAddress(request.detailAddress ?? '');
    setAvailabilities(
      request.availabilities?.length ? request.availabilities : [{ dayOfWeek: 0, startTime: '', endTime: '' }]
    );
    if (request.districts.length) {
      void getAllDistricts().then((all) => {
        const district = all.find((item) => item.id === request.districts[0].id);
        if (district) setProvinceId(district.provinceId);
      });
    }
    setMenuId(null);
    setFormOpen(true);
  };

  const runAction = async (
    request: TeachingRequest,
    action: 'open' | 'close' | 'cancel' | 'pay-wallet' | 'pay-vnpay'
  ) => {
    setBusyId(request.id);
    setMenuId(null);
    setMessage('');
    try {
      if (action === 'cancel') await cancelMyTeachingRequest(request.id);
      if (action === 'open' || action === 'close')
        await updateMyTeachingRequestStatus(request.id, action.toUpperCase() as 'OPEN' | 'CLOSED');
      if (action === 'pay-wallet') await payTeachingRequestByWallet(request.id);
      if (action === 'pay-vnpay') {
        const paymentTab = window.open('about:blank', '_blank');
        try {
          const payment = await startTeachingRequestVnPay(request.id);
          if (!payment.paymentUrl) throw new Error('VNPay payment URL is unavailable.');
          if (paymentTab) paymentTab.location.href = payment.paymentUrl;
          else window.location.assign(payment.paymentUrl);
        } catch (caught) {
          paymentTab?.close();
          throw caught;
        }
      }
      setMessage(
        action === 'cancel'
          ? 'Đã hủy yêu cầu dạy.'
          : action === 'close'
            ? 'Đã đóng yêu cầu dạy.'
            : action === 'open'
              ? 'Đã mở lại yêu cầu dạy.'
              : action === 'pay-wallet'
                ? 'Đã thanh toán bằng ví và đăng yêu cầu.'
                : action === 'pay-vnpay'
                  ? 'Đã mở trang thanh toán VNPay. Yêu cầu vẫn là bản nháp nếu chưa thanh toán thành công.'
                  : 'Đã cập nhật yêu cầu dạy.'
      );
      reload();
    } catch (caught) {
      if (action === 'pay-wallet') {
        window.alert(teachingRequestWalletErrorMessage(caught));
        setMessage(teachingRequestWalletErrorMessage(caught));
      } else {
        setMessage(caught instanceof Error ? caught.message : 'Không thể cập nhật yêu cầu dạy.');
      }
    } finally {
      setBusyId(null);
    }
  };

  return (
    <section className="common-requests-page">
      <header className="student-page-heading common-requests-heading">
        <div>
          <h1>{formOpen ? (editing ? 'Chỉnh sửa yêu cầu dạy' : 'Tạo yêu cầu dạy') : 'Yêu cầu dạy của tôi'}</h1>
          {formOpen && <p>Cho học viên biết bạn có thể dạy môn học và khối lớp nào.</p>}
        </div>
        <button
          className="student-button draft common-create-button"
          type="button"
          onClick={() => {
            if (formOpen) {
              setFormOpen(false);
              setEditing(null);
            } else {
              setEditing(null);
              setSubjectIds([]);
              setGradeIds([]);
              setCustomSubjectName('');
              setQuantity('1');
              setTeachingMode('ONLINE');
              setProvinceId('');
              setDistrictIds([]);
              setDetailAddress('');
              setAvailabilities([{ dayOfWeek: 0, startTime: '', endTime: '' }]);
              setFormOpen(true);
            }
          }}
        >
          {formOpen ? 'Quay lại danh sách' : 'Tạo yêu cầu'}
        </button>
      </header>
      {message && (
        <p role="status" className="student-notice">
          {message}
        </p>
      )}
      {formOpen && (
        <form
          key={editing?.id ?? 'new'}
          className="student-panel student-form-grid common-request-form"
          onSubmit={(event) => void submit(event)}
        >
          <label className="student-field-full">
            Tiêu đề
            <input name="title" maxLength={255} defaultValue={editing?.title ?? ''} required />
          </label>
          <label className="student-field-full">
            Mô tả
            <textarea name="description" maxLength={10000} rows={3} defaultValue={editing?.description ?? ''} />
          </label>
          <TeachingSubjectsField subjects={subjects} values={subjectIds} onChange={setSubjectIds} />
          {subjectIds.includes('OTHER') && (
            <label>
              Tên môn học khác
              <input
                value={customSubjectName}
                onChange={(event) => setCustomSubjectName(event.target.value)}
                maxLength={100}
                required
              />
            </label>
          )}
          <MultiSelectDropdown
            className="student-field-full"
            label="Khối lớp có thể dạy"
            placeholder="Chọn một hoặc nhiều khối lớp"
            options={grades
              .filter((grade) => grade.status === 'ACTIVE')
              .map((grade) => ({ value: grade.id, label: grade.name }))}
            values={gradeIds}
            onChange={setGradeIds}
          />
          <label>
            Số lượng học viên{editing && editing.status !== 'DRAFT' && ' (Không thể đổi sau khi đã thanh toán)'}
            <input
              name="quantity"
              type="number"
              min="1"
              value={quantity}
              onChange={(event) => setQuantity(event.target.value)}
              readOnly={Boolean(editing && editing.status !== 'DRAFT')}
              required
            />
          </label>
          <label>
            Học phí dự kiến (VNĐ)
            <input
              name="expectedPrice"
              type="number"
              min="0"
              step="1000"
              defaultValue={editing?.expectedPrice ?? ''}
              required
            />
          </label>
          <RequestModeField label="Hình thức dạy" value={teachingMode} onChange={setTeachingMode} />
          <label>
            Khu vực
            <select
              value={provinceId}
              onChange={(event) => {
                setProvinceId(event.target.value);
                setDistrictIds([]);
                setDistricts([]);
              }}
            >
              <option value="">Chọn tỉnh/thành phố</option>
              {provinces.map((province) => (
                <option key={province.id} value={province.id}>
                  {province.name}
                </option>
              ))}
            </select>
          </label>
          <MultiSelectDropdown
            className="student-field-full"
            label="Quận/huyện có thể dạy"
            placeholder={!provinceId ? 'Chọn tỉnh/thành phố trước' : 'Không có quận/huyện để chọn'}
            options={districts.map((district) => ({ value: district.id, label: district.name }))}
            values={districtIds}
            onChange={setDistrictIds}
            disabled={!provinceId || districts.length === 0}
          />
          <label className="student-field-full">
            Địa chỉ chi tiết
            <input name="detailAddress" maxLength={255} defaultValue={detailAddress} />
          </label>
          <label className="student-field-full">
            Ghi chú
            <textarea name="note" maxLength={255} rows={2} defaultValue={editing?.note ?? ''} />
          </label>
          <StudentAvailabilityEditor
            title="Khung giờ có thể dạy (không bắt buộc)"
            values={availabilities}
            onChange={setAvailabilities}
          />
          {!editing && (
            <>
              <div className="student-field-full common-payment-summary">
                <strong>Số tiền cần thanh toán: {amountDue.toLocaleString('vi-VN')} VNĐ</strong>
                <span>
                  {TEACHING_REQUEST_FEE_PER_STUDENT.toLocaleString('vi-VN')} VNĐ / 1 học viên × {learnerCount} học viên.
                </span>
                <span>
                  Phí đăng bài không được hoàn lại sau khi thanh toán. Yêu cầu có môn học chưa có trong danh mục sẽ chờ
                  duyệt; chỉ trường hợp này nếu bị quản trị viên từ chối do không hợp lệ mới được hoàn phí.
                </span>
              </div>
              <label className="common-payment-field">
                Phương thức thanh toán
                <select name="paymentMethod" defaultValue="wallet">
                  <option value="wallet">Ví điện tử</option>
                  <option value="vnpay">VNPay</option>
                </select>
              </label>
            </>
          )}
          <div className="student-form-actions common-request-actions">
            <button
              className="student-button secondary"
              type="button"
              onClick={() => {
                setFormOpen(false);
                setEditing(null);
                setSubjectIds([]);
                setGradeIds([]);
                setCustomSubjectName('');
                setQuantity('1');
                setTeachingMode('ONLINE');
                setProvinceId('');
                setDistrictIds([]);
                setDetailAddress('');
                setAvailabilities([{ dayOfWeek: 0, startTime: '', endTime: '' }]);
              }}
            >
              Hủy
            </button>
            {editing ? (
              <button className="student-button draft" name="submitAction" value="draft" disabled={busy}>
                {busy ? 'Đang xử lý…' : 'Lưu thay đổi'}
              </button>
            ) : (
              <>
                <button className="student-button draft" name="submitAction" value="draft" disabled={busy}>
                  {busy ? 'Đang xử lý…' : 'Tạo bản nháp'}
                </button>
                <button className="student-button draft" name="submitAction" value="pay" disabled={busy}>
                  {busy ? 'Đang xử lý…' : 'Tạo và thanh toán'}
                </button>
              </>
            )}
          </div>
        </form>
      )}
      {!formOpen && (
        <>
          <div className="common-request-toolbar">
            <label>
              Tìm kiếm
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Tìm theo tiêu đề hoặc môn học"
              />
            </label>
            <label>
              Trạng thái
              <select value={status} onChange={(event) => setStatus(event.target.value)}>
                <option value="ALL">Tất cả trạng thái</option>
                {['DRAFT', 'PENDING_REVIEW', 'OPEN', 'MATCHED', 'CLOSED', 'CANCELLED', 'REJECTED'].map((value) => (
                  <option key={value} value={value}>
                    {statusText(value)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Sắp xếp
              <select value={sort} onChange={(event) => setSort(event.target.value as 'DESC' | 'ASC')}>
                <option value="DESC">Mới nhất trước</option>
                <option value="ASC">Cũ nhất trước</option>
              </select>
            </label>
          </div>
          {error && <p role="alert">{error}</p>}
          {isLoading ? (
            <div className="student-card-grid">
              {[1, 2, 3].map((item) => (
                <div className="student-skeleton" key={item} />
              ))}
            </div>
          ) : requests.length ? (
            <div className="student-card-grid">
              {visibleRequests.map((request) => {
                const requestStatus = request.status ?? 'OPEN';
                const canEdit = ['DRAFT', 'OPEN', 'PENDING_REVIEW'].includes(requestStatus);
                const canPay = requestStatus === 'DRAFT';
                const canClose = requestStatus === 'OPEN' && !request.reviewedAt;
                const canReopen = requestStatus === 'CLOSED' && !request.reviewedAt;
                const canCancel = requestStatus === 'DRAFT' || requestStatus === 'PENDING_REVIEW';
                const hasActions = canEdit || canPay || canClose || canReopen || canCancel;
                return (
                  <article
                    className={`student-panel student-request-card status-${requestStatus.toLowerCase()}`}
                    key={request.id}
                    onClick={() => navigate(`/tutor/sections/teaching-requests/${request.id}`)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') navigate(`/tutor/sections/teaching-requests/${request.id}`);
                    }}
                    tabIndex={0}
                    role="link"
                  >
                    <div className="student-card-top">
                      <span className="student-tag">
                        {request.subjectName || request.customSubjectName || 'Môn học tùy chỉnh'}
                      </span>
                      <div className="student-card-menu" ref={menuId === request.id ? menuRef : undefined}>
                        <span className={`student-status ${requestStatus.toLowerCase()}`}>
                          {statusText(requestStatus)}
                        </span>
                        {hasActions && (
                          <>
                            <button
                              type="button"
                              aria-label="Thao tác yêu cầu"
                              aria-expanded={menuId === request.id}
                              disabled={busyId === request.id}
                              onClick={(event) => {
                                event.stopPropagation();
                                setMenuId((current) => (current === request.id ? null : request.id));
                              }}
                            >
                              ⋯
                            </button>
                            {menuId === request.id && (
                              <div className="student-request-menu" onClick={(event) => event.stopPropagation()}>
                                {canEdit && (
                                  <button type="button" onClick={() => editRequest(request)}>
                                    Chỉnh sửa thông tin
                                  </button>
                                )}
                                {canPay && (
                                  <>
                                    <button
                                      className="payment"
                                      type="button"
                                      onClick={() => void runAction(request, 'pay-wallet')}
                                    >
                                      Thanh toán bằng ví điện tử
                                    </button>
                                    <button
                                      className="payment"
                                      type="button"
                                      onClick={() => void runAction(request, 'pay-vnpay')}
                                    >
                                      Thanh toán bằng VNPay
                                    </button>
                                  </>
                                )}
                                {canClose && (
                                  <button type="button" onClick={() => void runAction(request, 'close')}>
                                    Đóng yêu cầu
                                  </button>
                                )}
                                {canReopen && (
                                  <button type="button" onClick={() => void runAction(request, 'open')}>
                                    Mở lại yêu cầu
                                  </button>
                                )}
                                {canCancel && (
                                  <button
                                    className="danger"
                                    type="button"
                                    onClick={() => void runAction(request, 'cancel')}
                                  >
                                    Hủy yêu cầu
                                  </button>
                                )}
                              </div>
                            )}
                          </>
                        )}
                      </div>
                    </div>
                    <h3>{request.title}</h3>
                    <p>{request.description || request.note || 'Chưa có mô tả.'}</p>
                    {requestStatus === 'REJECTED' && request.rejectionReason && (
                      <p className="teaching-rejection-reason" role="alert">
                        <strong>Lý do từ chối:</strong> {request.rejectionReason}
                      </p>
                    )}
                    <div className="student-card-actions">
                      <span>
                        {request.grades.map((grade) => grade.name).join(', ') || 'Chưa chọn khối'} ·{' '}
                        {request.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}
                      </span>
                      <span className="student-card-created">
                        Tạo lúc{' '}
                        {new Date(request.createdAt).toLocaleString('vi-VN', {
                          day: '2-digit',
                          month: 'short',
                          hour: '2-digit',
                          minute: '2-digit'
                        })}
                      </span>
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            <div className="student-empty">
              <h3>Chưa có yêu cầu trong danh sách này</h3>
              <p>Hãy đổi bộ lọc hoặc tạo yêu cầu dạy mới.</p>
            </div>
          )}
          {requests.length > 0 && pageCount > 1 && (
            <nav className="common-request-pagination" aria-label="Phân trang yêu cầu dạy">
              <span>
                Hiển thị {(activePage - 1) * pageSize + 1}–{Math.min(activePage * pageSize, requests.length)} trong{' '}
                {requests.length} yêu cầu
              </span>
              <div className="common-request-pagination-pages">
                <button type="button" onClick={() => goToPage(Math.max(1, activePage - 1))} disabled={activePage === 1}>
                  Trước
                </button>
                {firstPageButton > 1 && (
                  <>
                    <button type="button" onClick={() => goToPage(1)}>
                      1
                    </button>
                    {firstPageButton > 2 && <span aria-hidden="true">…</span>}
                  </>
                )}
                {Array.from(
                  { length: lastPageButton - firstPageButton + 1 },
                  (_, index) => firstPageButton + index
                ).map((pageNumber) => (
                  <button
                    key={pageNumber}
                    type="button"
                    onClick={() => goToPage(pageNumber)}
                    aria-current={pageNumber === activePage ? 'page' : undefined}
                  >
                    {pageNumber}
                  </button>
                ))}
                {lastPageButton < pageCount && (
                  <>
                    {lastPageButton < pageCount - 1 && <span aria-hidden="true">…</span>}
                    <button type="button" onClick={() => goToPage(pageCount)}>
                      {pageCount}
                    </button>
                  </>
                )}
                <button
                  type="button"
                  onClick={() => goToPage(Math.min(pageCount, activePage + 1))}
                  disabled={activePage === pageCount}
                >
                  Sau
                </button>
              </div>
            </nav>
          )}
        </>
      )}
    </section>
  );
}

function statusText(status: string) {
  return (
    (
      {
        DRAFT: 'Bản nháp',
        PENDING_REVIEW: 'Chờ duyệt',
        OPEN: 'Đang mở',
        MATCHED: 'Đã kết nối',
        CLOSED: 'Đã đóng',
        CANCELLED: 'Đã hủy',
        REJECTED: 'Bị từ chối'
      } as Record<string, string>
    )[status] ?? status
  );
}

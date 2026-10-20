import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../../services/httpClient';
import { getDistricts, getProvinces } from '../../services/locationApi';
import { getStudentProfile, updateStudentProfile, type StudentProfile } from '../../services/studentApi';
import type { District, Province } from '../../types/api';

export function StudentProfilePage() {
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [provinces, setProvinces] = useState<Province[]>([]);
  const [districts, setDistricts] = useState<District[]>([]);
  const [provinceId, setProvinceId] = useState('');
  const [districtId, setDistrictId] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    getStudentProfile()
      .then((value) => {
        setProfile(value);
        setProvinceId(value.address?.provinceId || '');
        setDistrictId(value.address?.districtId || '');
      })
      .catch((caught) => setError(caught instanceof Error ? caught.message : 'Không tải được hồ sơ của bạn.'));
    getProvinces()
      .then(setProvinces)
      .catch(() => undefined);
  }, []);
  useEffect(() => {
    if (provinceId)
      getDistricts(provinceId)
        .then(setDistricts)
        .catch(() => setDistricts([]));
  }, [provinceId]);
  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!profile) return;
    const values = new FormData(event.currentTarget);
    const text = (key: string) => String(values.get(key) ?? '').trim() || undefined;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const updated = await updateStudentProfile({
        firstName: text('firstName'),
        lastName: text('lastName'),
        phone: text('phone'),
        gender: text('gender') as StudentProfile['gender'],
        dateOfBirth: text('dateOfBirth'),
        avatarUrl: text('avatarUrl'),
        detailAddress: text('detailAddress'),
        provinceId: provinceId || undefined,
        districtId: districtId || undefined,
        introduction: text('introduction'),
        learningStyle: text('learningStyle'),
        personalityTags: text('personalityTags'),
        goalsDescription: text('goalsDescription'),
        currentLevel: text('currentLevel'),
        weakPoints: text('weakPoints')
      });
      setProfile(updated);
      setNotice('Đã lưu hồ sơ.');
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? Object.values(caught.fieldErrors).join(' ') || caught.message
          : caught instanceof Error
            ? caught.message
            : 'Chưa lưu được hồ sơ.'
      );
    } finally {
      setSaving(false);
    }
  };
  if (!profile)
    return error ? (
      <div className="student-error" role="alert">
        {error}
      </div>
    ) : (
      <div className="student-empty">
        <h3>Đang tải hồ sơ…</h3>
      </div>
    );
  return (
    <>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">TÀI KHOẢN</span>
          <h1>Hồ sơ của tôi</h1>
          <p>Cập nhật thông tin cá nhân và nhu cầu học của bạn.</p>
        </div>
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
      <form className="student-panel" onSubmit={(event) => void save(event)}>
        <div className="student-form-grid">
          <label>
            Tên
            <input name="firstName" defaultValue={profile.firstName || ''} />
          </label>
          <label>
            Họ
            <input name="lastName" defaultValue={profile.lastName || ''} />
          </label>
          <label>
            Email
            <input value={profile.email} readOnly />
          </label>
          <label>
            Số điện thoại
            <input name="phone" defaultValue={profile.phone || ''} />
          </label>
          <label>
            Ngày sinh
            <input name="dateOfBirth" type="date" defaultValue={profile.dateOfBirth || ''} />
          </label>
          <label>
            Giới tính
            <select name="gender" defaultValue={profile.gender || ''}>
              <option value="">Chọn</option>
              <option value="MALE">Nam</option>
              <option value="FEMALE">Nữ</option>
              <option value="OTHER">Khác</option>
            </select>
          </label>
          <label>
            Tỉnh / thành
            <select
              value={provinceId}
              onChange={(event) => {
                setProvinceId(event.target.value);
                setDistrictId('');
              }}
            >
              <option value="">Chọn tỉnh / thành</option>
              {provinces.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Quận / huyện
            <select value={districtId} onChange={(event) => setDistrictId(event.target.value)} disabled={!provinceId}>
              <option value="">Chọn quận / huyện</option>
              {districts.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
          <label className="student-field-full">
            Đường dẫn ảnh đại diện
            <input name="avatarUrl" type="url" defaultValue={profile.avatarUrl || ''} />
          </label>
          <label className="student-field-full">
            Giới thiệu
            <textarea name="introduction" rows={3} defaultValue={profile.introduction || ''} />
          </label>
          <label>
            Phong cách học
            <input name="learningStyle" defaultValue={profile.learningStyle || ''} />
          </label>
          <label>
            Trình độ hiện tại
            <input name="currentLevel" defaultValue={profile.currentLevel || ''} />
          </label>
          <label className="student-field-full">
            Mục tiêu
            <textarea name="goalsDescription" rows={3} defaultValue={profile.goalsDescription || ''} />
          </label>
          <label>
            Tính cách
            <input name="personalityTags" defaultValue={profile.personalityTags || ''} />
          </label>
          <label>
            Điểm yếu cần cải thiện
            <input name="weakPoints" defaultValue={profile.weakPoints || ''} />
          </label>
        </div>
        <div className="student-form-actions">
          <button className="student-button" type="submit" disabled={saving}>
            {saving ? 'Đang lưu…' : 'Lưu hồ sơ'}
          </button>
        </div>
      </form>
    </>
  );
}

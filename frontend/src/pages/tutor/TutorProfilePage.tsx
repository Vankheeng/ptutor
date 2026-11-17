import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../../services/httpClient';
import { getTutorProfile, updateTutorProfile, type TutorProfile } from '../../services/tutorApi';
import { useTutorProfile } from '../../hooks/useTutorWorkspace';
import { useAuth } from '../../auth/useAuth';
import { getDistricts, getProvinces } from '../../services/locationApi';
import type { District, Province } from '../../types/api';
import { TutorPersonalInformationSection } from './components/TutorPersonalInformationSection';
import { TutorTeachingProfileSection } from './components/TutorTeachingProfileSection';

export function TutorProfilePage({ onSaved }: { onSaved?: () => void }) {
  const { updateProfile } = useAuth();
  const { data: loadedProfile, isLoading: loading, error: loadError } = useTutorProfile();
  const [savedProfile, setSavedProfile] = useState<TutorProfile | null>(null);
  const [formRevision, setFormRevision] = useState(0);
  const profile = savedProfile ?? loadedProfile;
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [provinces, setProvinces] = useState<Province[]>([]);
  const [districts, setDistricts] = useState<District[]>([]);
  const [selectedProvinceId, setSelectedProvinceId] = useState<string | null>(null);
  const [selectedDistrictId, setSelectedDistrictId] = useState<string | null>(null);
  const provinceId = selectedProvinceId ?? profile?.address?.provinceId ?? '';
  const districtId = selectedDistrictId ?? profile?.address?.districtId ?? '';

  useEffect(() => {
    getProvinces()
      .then(setProvinces)
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!provinceId) {
      return;
    }
    getDistricts(provinceId)
      .then(setDistricts)
      .catch(() => setDistricts([]));
  }, [provinceId]);

  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const optionalText = (key: string) => String(form.get(key) ?? '').trim() || null;
    const selectedProvince = String(form.get('provinceId') ?? '');
    const selectedDistrict = String(form.get('districtId') ?? '');
    if (Boolean(selectedProvince) !== Boolean(selectedDistrict)) {
      setError('Vui lòng chọn cả tỉnh/thành phố và quận/huyện, hoặc để trống cả hai.');
      return;
    }
    setSaving(true);
    setNotice('');
    setError('');
    try {
      await updateTutorProfile({
        firstName: optionalText('firstName'),
        lastName: optionalText('lastName'),
        phone: optionalText('phone'),
        gender: optionalText('gender'),
        dateOfBirth: optionalText('dateOfBirth'),
        detailAddress: optionalText('detailAddress'),
        ...(selectedProvince && selectedDistrict ? { provinceId: selectedProvince, districtId: selectedDistrict } : {}),
        introduction: optionalText('introduction'),
        experienceYears: form.get('experienceYears') ? Number(form.get('experienceYears')) : null,
        education: optionalText('education'),
        teachingStyleTags: optionalText('teachingStyleTags'),
        teachingMethodology: optionalText('teachingMethodology'),
        strengthSubjects: optionalText('strengthSubjects'),
        targetStudentType: optionalText('targetStudentType')
      });
      const persistedProfile = await getTutorProfile();
      setSavedProfile(persistedProfile);
      updateProfile(persistedProfile);
      setSelectedProvinceId(null);
      setSelectedDistrictId(null);
      setFormRevision((value) => value + 1);
      onSaved?.();
      setNotice('Hồ sơ của bạn đã được lưu.');
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? Object.values(caught.fieldErrors).join(' ') || caught.message
          : caught instanceof Error
            ? caught.message
            : 'Không thể lưu hồ sơ. Vui lòng thử lại.'
      );
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="tutor-empty">Đang tải hồ sơ…</div>;
  if (!profile)
    return (
      <div className="tutor-error" role="alert">
        {error || loadError || 'Không tìm thấy hồ sơ gia sư của bạn.'}
      </div>
    );
  return (
    <>
      {profile.profileStatus === 'REJECTED' && (
        <div className="tutor-error" role="status">
          <strong>Hồ sơ chưa được duyệt.</strong>{' '}
          {profile.profileRejectionReason && <>Lý do: {profile.profileRejectionReason}. </>}
          Cập nhật thông tin giảng dạy và lưu để gửi xét duyệt lại.
        </div>
      )}
      {notice && (
        <div className="tutor-notice" role="status">
          {notice}
        </div>
      )}
      {(error || loadError) && (
        <div className="tutor-error" role="alert">
          {error || loadError}
        </div>
      )}
      <form className="tutor-profile-form" key={formRevision} onSubmit={(event) => void save(event)}>
        <div className="tutor-profile-columns profile-edit-sections">
          <TutorPersonalInformationSection
            profile={profile}
            provinces={provinces}
            districts={districts}
            provinceId={provinceId}
            districtId={districtId}
            onProvinceChange={(value) => {
              setSelectedProvinceId(value);
              setSelectedDistrictId('');
            }}
            onDistrictChange={setSelectedDistrictId}
          />
          <TutorTeachingProfileSection profile={profile} />
        </div>
        <div className="tutor-profile-footer">
          <button className="tutor-button" type="submit" disabled={saving}>
            {saving ? 'Đang lưu…' : 'Lưu hồ sơ'}
          </button>
        </div>
      </form>
    </>
  );
}

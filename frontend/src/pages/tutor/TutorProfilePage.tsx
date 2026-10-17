import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../../services/httpClient';
import { updateTutorProfile, uploadCertificateFile } from '../../services/tutorApi';
import { useTutorProfile } from '../../hooks/useTutorWorkspace';
import { useAuth } from '../../auth/useAuth';
import { getDistricts, getProvinces } from '../../services/locationApi';
import type { District, Province } from '../../types/api';
import { TutorProfilePhotoSection } from './components/TutorProfilePhotoSection';
import { TutorPersonalInformationSection } from './components/TutorPersonalInformationSection';
import { TutorTeachingProfileSection } from './components/TutorTeachingProfileSection';

export function TutorProfilePage() {
  const { updateProfile } = useAuth();
  const [revision, setRevision] = useState(0);
  const { data: profile, isLoading: loading, error: loadError } = useTutorProfile(revision);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [photoReference, setPhotoReference] = useState<string | null>(null);
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
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
      const updatedProfile = await updateTutorProfile({
        firstName: optionalText('firstName'),
        lastName: optionalText('lastName'),
        phone: optionalText('phone'),
        gender: optionalText('gender'),
        dateOfBirth: optionalText('dateOfBirth'),
        avatarUrl: photoReference ?? optionalText('avatarUrl'),
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
      updateProfile(updatedProfile);
      setSelectedProvinceId(null);
      setSelectedDistrictId(null);
      setPhotoReference(null);
      setPhotoPreview(null);
      setRevision((value) => value + 1);
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
  const initials =
    [profile.firstName, profile.lastName]
      .filter(Boolean)
      .map((part) => part?.[0])
      .join('')
      .toUpperCase() || 'T';
  const uploadPhoto = async (file?: File) => {
    if (!file) return;
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      setError('Vui lòng chọn ảnh JPG hoặc PNG.');
      return;
    }
    setUploadingPhoto(true);
    setError('');
    try {
      const { certificateUrl, previewUrl } = await uploadCertificateFile(file);
      setPhotoReference(certificateUrl);
      setPhotoPreview(previewUrl);
      setNotice('Đã tải ảnh lên. Hãy lưu hồ sơ để áp dụng ảnh này.');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể tải ảnh lên.');
    } finally {
      setUploadingPhoto(false);
    }
  };

  return (
    <>
      <div className="tutor-page-heading">
        <div>
          <span className="tutor-eyebrow">HỒ SƠ CÔNG KHAI</span>
          <h1>Chỉnh sửa hồ sơ</h1>
          <p>Giúp học viên hiểu hơn về kinh nghiệm và phương pháp giảng dạy của bạn.</p>
        </div>
        <span className="tutor-profile-badge">Hồ sơ gia sư</span>
      </div>
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
      <form className="tutor-profile-form" key={revision} onSubmit={(event) => void save(event)}>
        <TutorProfilePhotoSection
          avatarUrl={photoPreview || profile.avatarUrl}
          initials={initials}
          uploading={uploadingPhoto}
          onUpload={(file) => void uploadPhoto(file)}
        />
        <div className="tutor-profile-columns tutor-profile-equal-columns">
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
          <span>Thông tin chuyên môn có thể được Ptutor xem xét.</span>
          <button className="tutor-button" type="submit" disabled={saving}>
            {saving ? 'Đang lưu…' : 'Lưu hồ sơ'}
          </button>
        </div>
      </form>
    </>
  );
}

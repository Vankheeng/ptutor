import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../../services/httpClient';
import { updateTutorProfile } from '../../services/tutorApi';
import { useTutorProfile } from '../../hooks/useTutorWorkspace';
import { getDistricts, getProvinces } from '../../services/locationApi';
import type { District, Province } from '../../types/api';

export function TutorProfilePage() {
  const [revision, setRevision] = useState(0);
  const { data: profile, isLoading: loading, error: loadError } = useTutorProfile(revision);
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
      setError('Select both a province and district, or leave both blank.');
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
        avatarUrl: optionalText('avatarUrl'),
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
      setSelectedProvinceId(null);
      setSelectedDistrictId(null);
      setRevision((value) => value + 1);
      setNotice('Your profile has been saved.');
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? Object.values(caught.fieldErrors).join(' ') || caught.message
          : caught instanceof Error
            ? caught.message
            : 'Could not save your profile.'
      );
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="tutor-empty">Loading your profile…</div>;
  if (!profile)
    return (
      <div className="tutor-error" role="alert">
        {error || loadError || 'Your tutor profile could not be found.'}
      </div>
    );
  const initials =
    [profile.firstName, profile.lastName]
      .filter(Boolean)
      .map((part) => part?.[0])
      .join('')
      .toUpperCase() || 'T';

  return (
    <>
      <div className="tutor-page-heading">
        <div>
          <span className="tutor-eyebrow">YOUR PUBLIC PRESENCE</span>
          <h1>Edit user profile</h1>
          <p>Help students understand your experience and how you teach.</p>
        </div>
        <span className="tutor-profile-badge">Tutor profile</span>
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
        <section className="tutor-panel tutor-profile-cover">
          <div className="tutor-cover-art">
            <span />
            <i />
            <b />
          </div>
          <div className="tutor-photo-row">
            <span className="tutor-profile-photo">
              {profile.avatarUrl ? <img src={profile.avatarUrl} alt="Profile" /> : initials}
            </span>
            <div>
              <strong>Your photo</strong>
              <small>Use a public image URL for your profile photo.</small>
            </div>
            <label className="tutor-avatar-url">
              Photo URL
              <input name="avatarUrl" type="url" defaultValue={profile.avatarUrl ?? ''} placeholder="https://…" />
            </label>
          </div>
        </section>
        <div className="tutor-profile-columns">
          <section className="tutor-panel tutor-form-panel">
            <div className="tutor-panel-heading">
              <span>01</span>
              <div>
                <h2>Personal information</h2>
                <p>Your basic contact and identity details.</p>
              </div>
            </div>
            <div className="tutor-form-grid">
              <label>
                First name
                <input name="firstName" defaultValue={profile.firstName ?? ''} maxLength={100} />
              </label>
              <label>
                Last name
                <input name="lastName" defaultValue={profile.lastName ?? ''} maxLength={100} />
              </label>
              <label>
                Email address
                <input value={profile.email} readOnly />
              </label>
              <label>
                Mobile number
                <input name="phone" defaultValue={profile.phone ?? ''} maxLength={20} />
              </label>
              <label>
                Date of birth
                <input name="dateOfBirth" type="date" defaultValue={profile.dateOfBirth ?? ''} />
              </label>
              <label>
                Gender
                <select name="gender" defaultValue={profile.gender ?? ''}>
                  <option value="">Choose</option>
                  <option value="FEMALE">Female</option>
                  <option value="MALE">Male</option>
                  <option value="OTHER">Other</option>
                </select>
              </label>
              <label>
                Province
                <select
                  name="provinceId"
                  value={provinceId}
                  onChange={(event) => {
                    setSelectedProvinceId(event.target.value);
                    setSelectedDistrictId('');
                  }}
                >
                  <option value="">Choose a province</option>
                  {provinces.map((province) => (
                    <option key={province.id} value={province.id}>
                      {province.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                District
                <select
                  name="districtId"
                  value={districtId}
                  onChange={(event) => setSelectedDistrictId(event.target.value)}
                  disabled={!provinceId}
                >
                  <option value="">Choose a district</option>
                  {districts.map((district) => (
                    <option key={district.id} value={district.id}>
                      {district.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="tutor-field-full">
                Street address
                <input name="detailAddress" defaultValue={profile.address?.detailAddress ?? ''} maxLength={255} />
              </label>
            </div>
          </section>
          <section className="tutor-panel tutor-form-panel">
            <div className="tutor-panel-heading">
              <span>02</span>
              <div>
                <h2>Teaching profile</h2>
                <p>Share what makes your classes a good fit.</p>
              </div>
            </div>
            <div className="tutor-form-grid">
              <label className="tutor-field-full">
                Bio / introduction
                <textarea
                  name="introduction"
                  rows={5}
                  defaultValue={profile.introduction ?? ''}
                  placeholder="Tell students about your background and teaching approach."
                />
              </label>
              <label>
                Years of experience
                <input name="experienceYears" type="number" min="0" defaultValue={profile.experienceYears ?? ''} />
              </label>
              <label>
                Education
                <input name="education" defaultValue={profile.education ?? ''} />
              </label>
              <label className="tutor-field-full">
                Subjects you teach
                <input
                  name="strengthSubjects"
                  defaultValue={profile.strengthSubjects ?? ''}
                  placeholder="Mathematics, Physics, IELTS…"
                />
              </label>
              <label className="tutor-field-full">
                Teaching style tags
                <input
                  name="teachingStyleTags"
                  defaultValue={profile.teachingStyleTags ?? ''}
                  placeholder="Patient, practical, exam focused…"
                />
              </label>
              <label className="tutor-field-full">
                Teaching methodology
                <textarea name="teachingMethodology" rows={3} defaultValue={profile.teachingMethodology ?? ''} />
              </label>
              <label className="tutor-field-full">
                Students you work best with
                <input
                  name="targetStudentType"
                  defaultValue={profile.targetStudentType ?? ''}
                  placeholder="Primary school, exam preparation…"
                />
              </label>
            </div>
          </section>
        </div>
        <div className="tutor-profile-footer">
          <span>Changes to professional details may be reviewed by Ptutor.</span>
          <button className="tutor-button" type="submit" disabled={saving}>
            {saving ? 'Saving…' : 'Save profile'}
          </button>
        </div>
      </form>
    </>
  );
}

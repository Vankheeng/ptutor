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
      .catch((caught) => setError(caught instanceof Error ? caught.message : 'Could not load your profile.'));
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
  if (!profile)
    return error ? (
      <div className="student-error" role="alert">
        {error}
      </div>
    ) : (
      <div className="student-empty">
        <h3>Loading profile…</h3>
      </div>
    );
  return (
    <>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">YOUR ACCOUNT</span>
          <h1>My Profile</h1>
          <p>Keep your personal and learning information up to date.</p>
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
            First name
            <input name="firstName" defaultValue={profile.firstName || ''} />
          </label>
          <label>
            Last name
            <input name="lastName" defaultValue={profile.lastName || ''} />
          </label>
          <label>
            Email
            <input value={profile.email} readOnly />
          </label>
          <label>
            Phone
            <input name="phone" defaultValue={profile.phone || ''} />
          </label>
          <label>
            Date of birth
            <input name="dateOfBirth" type="date" defaultValue={profile.dateOfBirth || ''} />
          </label>
          <label>
            Gender
            <select name="gender" defaultValue={profile.gender || ''}>
              <option value="">Choose</option>
              <option value="MALE">Male</option>
              <option value="FEMALE">Female</option>
              <option value="OTHER">Other</option>
            </select>
          </label>
          <label>
            Province
            <select
              value={provinceId}
              onChange={(event) => {
                setProvinceId(event.target.value);
                setDistrictId('');
              }}
            >
              <option value="">Choose a province</option>
              {provinces.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            District
            <select value={districtId} onChange={(event) => setDistrictId(event.target.value)} disabled={!provinceId}>
              <option value="">Choose a district</option>
              {districts.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
          <label className="student-field-full">
            Avatar URL
            <input name="avatarUrl" type="url" defaultValue={profile.avatarUrl || ''} />
          </label>
          <label className="student-field-full">
            Introduction
            <textarea name="introduction" rows={3} defaultValue={profile.introduction || ''} />
          </label>
          <label>
            Learning style
            <input name="learningStyle" defaultValue={profile.learningStyle || ''} />
          </label>
          <label>
            Current level
            <input name="currentLevel" defaultValue={profile.currentLevel || ''} />
          </label>
          <label className="student-field-full">
            Goals
            <textarea name="goalsDescription" rows={3} defaultValue={profile.goalsDescription || ''} />
          </label>
          <label>
            Personality tags
            <input name="personalityTags" defaultValue={profile.personalityTags || ''} />
          </label>
          <label>
            Weak points
            <input name="weakPoints" defaultValue={profile.weakPoints || ''} />
          </label>
        </div>
        <div className="student-form-actions">
          <button className="student-button" type="submit" disabled={saving}>
            {saving ? 'Saving…' : 'Save profile'}
          </button>
        </div>
      </form>
    </>
  );
}

import type { District, Province } from '../../../types/api';
import type { TutorProfile } from '../../../services/tutorApi';

interface TutorPersonalInformationSectionProps {
  profile: TutorProfile;
  provinces: Province[];
  districts: District[];
  provinceId: string;
  districtId: string;
  onProvinceChange: (value: string) => void;
  onDistrictChange: (value: string) => void;
}

export function TutorPersonalInformationSection({
  profile,
  provinces,
  districts,
  provinceId,
  districtId,
  onProvinceChange,
  onDistrictChange
}: TutorPersonalInformationSectionProps) {
  return (
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
          <select name="provinceId" value={provinceId} onChange={(event) => onProvinceChange(event.target.value)}>
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
            onChange={(event) => onDistrictChange(event.target.value)}
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
  );
}

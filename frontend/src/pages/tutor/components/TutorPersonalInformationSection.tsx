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
          <h2>Thông tin cá nhân</h2>
          <p>Thông tin liên hệ và nhận dạng cơ bản của bạn.</p>
        </div>
      </div>
      <div className="tutor-form-grid">
        <label>
          Tên
          <input name="firstName" defaultValue={profile.firstName ?? ''} maxLength={100} />
        </label>
        <label>
          Họ
          <input name="lastName" defaultValue={profile.lastName ?? ''} maxLength={100} />
        </label>
        <label>
          Địa chỉ email
          <input value={profile.email} readOnly />
        </label>
        <label>
          Số điện thoại
          <input name="phone" defaultValue={profile.phone ?? ''} maxLength={20} />
        </label>
        <label>
          Ngày sinh
          <input name="dateOfBirth" type="date" defaultValue={profile.dateOfBirth ?? ''} />
        </label>
        <label>
          Giới tính
          <select name="gender" defaultValue={profile.gender ?? ''}>
            <option value="">Chọn</option>
            <option value="FEMALE">Nữ</option>
            <option value="MALE">Nam</option>
            <option value="OTHER">Khác</option>
          </select>
        </label>
        <label>
          Tỉnh/thành phố
          <select name="provinceId" value={provinceId} onChange={(event) => onProvinceChange(event.target.value)}>
            <option value="">Chọn tỉnh/thành phố</option>
            {provinces.map((province) => (
              <option key={province.id} value={province.id}>
                {province.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Quận/huyện
          <select
            name="districtId"
            value={districtId}
            onChange={(event) => onDistrictChange(event.target.value)}
            disabled={!provinceId}
          >
            <option value="">Chọn quận/huyện</option>
            {districts.map((district) => (
              <option key={district.id} value={district.id}>
                {district.name}
              </option>
            ))}
          </select>
        </label>
        <label className="tutor-field-full">
          Địa chỉ cụ thể
          <input name="detailAddress" defaultValue={profile.address?.detailAddress ?? ''} maxLength={255} />
        </label>
      </div>
    </section>
  );
}

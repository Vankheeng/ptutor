import type { TutorProfile } from '../../../services/tutorApi';

export function TutorTeachingProfileSection({ profile }: { profile: TutorProfile }) {
  return (
    <section className="tutor-panel tutor-form-panel">
      <div className="tutor-panel-heading">
        <span>02</span>
        <div>
          <h2>Hồ sơ dạy học</h2>
        </div>
      </div>
      <div className="tutor-form-grid">
        <label className="tutor-field-full">
          Giới thiệu
          <textarea
            name="introduction"
            rows={5}
            defaultValue={profile.introduction ?? ''}
            placeholder="Kể về bản thân và cách bạn dạy."
          />
        </label>
        <label>
          Số năm kinh nghiệm
          <input name="experienceYears" type="number" min="0" defaultValue={profile.experienceYears ?? ''} />
        </label>
        <label>
          Học vấn
          <input name="education" defaultValue={profile.education ?? ''} />
        </label>
        <label className="tutor-field-full">
          Môn dạy tốt
          <input
            name="strengthSubjects"
            defaultValue={profile.strengthSubjects ?? ''}
            placeholder="Toán, Vật lý, IELTS…"
          />
        </label>
        <label className="tutor-field-full">
          Phong cách dạy
          <input
            name="teachingStyleTags"
            defaultValue={profile.teachingStyleTags ?? ''}
            placeholder="Kiên nhẫn, thực tế, tập trung luyện thi…"
          />
        </label>
        <label className="tutor-field-full">
          Phương pháp dạy
          <textarea name="teachingMethodology" rows={3} defaultValue={profile.teachingMethodology ?? ''} />
        </label>
        <label className="tutor-field-full">
          Đối tượng học viên phù hợp
          <input
            name="targetStudentType"
            defaultValue={profile.targetStudentType ?? ''}
            placeholder="Tiểu học, luyện thi…"
          />
        </label>
      </div>
    </section>
  );
}

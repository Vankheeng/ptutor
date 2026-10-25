import type { TutorProfile } from '../../../services/tutorApi';

export function TutorTeachingProfileSection({ profile }: { profile: TutorProfile }) {
  return (
    <section className="tutor-panel tutor-form-panel">
      <div className="tutor-panel-heading">
        <span>02</span>
        <div>
          <h2>Hồ sơ giảng dạy</h2>
          <p>Giới thiệu điểm mạnh và phong cách giảng dạy của bạn.</p>
        </div>
      </div>
      <div className="tutor-form-grid">
        <label className="tutor-field-full">
          Giới thiệu bản thân
          <textarea
            name="introduction"
            rows={5}
            defaultValue={profile.introduction ?? ''}
            placeholder="Chia sẻ về kinh nghiệm và phương pháp giảng dạy của bạn."
          />
        </label>
        <label>
          Số năm kinh nghiệm
          <input name="experienceYears" type="number" min="0" defaultValue={profile.experienceYears ?? ''} />
        </label>
        <label>
          Trình độ học vấn
          <input name="education" defaultValue={profile.education ?? ''} />
        </label>
        <label className="tutor-field-full">
          Môn học giảng dạy
          <input
            name="strengthSubjects"
            defaultValue={profile.strengthSubjects ?? ''}
            placeholder="Toán, Vật lý, IELTS…"
          />
        </label>
        <label className="tutor-field-full">
          Từ khóa phong cách giảng dạy
          <input
            name="teachingStyleTags"
            defaultValue={profile.teachingStyleTags ?? ''}
            placeholder="Kiên nhẫn, thực tế, tập trung ôn thi…"
          />
        </label>
        <label className="tutor-field-full">
          Phương pháp giảng dạy
          <textarea name="teachingMethodology" rows={3} defaultValue={profile.teachingMethodology ?? ''} />
        </label>
        <label className="tutor-field-full">
          Đối tượng học viên phù hợp
          <input
            name="targetStudentType"
            defaultValue={profile.targetStudentType ?? ''}
            placeholder="Học sinh tiểu học, ôn thi…"
          />
        </label>
      </div>
    </section>
  );
}

import type { TutorProfile } from '../../../services/tutorApi';

export function TutorTeachingProfileSection({ profile }: { profile: TutorProfile }) {
  return (
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
  );
}

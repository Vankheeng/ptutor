import { useState } from 'react';

interface TutorProfilePhotoSectionProps {
  avatarUrl: string | null;
  initials: string;
  uploading: boolean;
  onUpload: (file?: File) => void;
}

export function TutorProfilePhotoSection({ avatarUrl, initials, uploading, onUpload }: TutorProfilePhotoSectionProps) {
  const [failedImageUrl, setFailedImageUrl] = useState<string | null>(null);
  const imageFailed = Boolean(avatarUrl) && failedImageUrl === avatarUrl;

  return (
    <section className="tutor-panel tutor-profile-cover">
      <div className="tutor-cover-art">
        <span />
        <i />
        <b />
      </div>
      <div className="tutor-photo-row">
        <span className="tutor-profile-photo">
          {imageFailed || !avatarUrl ? (
            initials
          ) : (
            <img src={avatarUrl} alt="Profile" onError={() => setFailedImageUrl(avatarUrl)} />
          )}
        </span>
        <div>
          <strong>Your photo</strong>
          <small>Upload a JPG or PNG image (up to 10 MB).</small>
        </div>
        <label className="tutor-avatar-url tutor-photo-upload">
          {uploading ? 'Uploading…' : 'Choose photo'}
          <input
            type="file"
            accept="image/jpeg,image/png"
            disabled={uploading}
            onChange={(event) => onUpload(event.target.files?.[0])}
          />
        </label>
      </div>
    </section>
  );
}

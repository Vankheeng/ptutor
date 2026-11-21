import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';

export function ProfileNameLink({
  role,
  profileId,
  children
}: {
  role: 'student' | 'tutor';
  profileId?: string | null;
  children: ReactNode;
}) {
  if (!profileId) return <>{children}</>;
  const path = role === 'student' ? `/tutor/students/${profileId}/profile` : `/student/tutors/${profileId}/profile`;
  return (
    <Link
      className="profile-name-link"
      to={path}
      onClick={(event) => event.stopPropagation()}
      onKeyDown={(event) => event.stopPropagation()}
    >
      {children}
    </Link>
  );
}

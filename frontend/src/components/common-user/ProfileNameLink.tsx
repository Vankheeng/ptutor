import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';

export function ProfileNameLink({
  role,
  profileId,
  returnTo,
  returnLabel,
  children
}: {
  role: 'student' | 'tutor';
  profileId?: string | null;
  returnTo?: string;
  returnLabel?: string;
  children: ReactNode;
}) {
  if (!profileId) return <>{children}</>;
  const path = role === 'student' ? `/tutor/students/${profileId}/profile` : `/student/tutors/${profileId}/profile`;
  return (
    <Link
      className="profile-name-link"
      to={path}
      state={returnTo ? { profileReturnTo: returnTo, profileReturnLabel: returnLabel } : undefined}
      onClick={(event) => event.stopPropagation()}
      onKeyDown={(event) => event.stopPropagation()}
    >
      {children}
    </Link>
  );
}

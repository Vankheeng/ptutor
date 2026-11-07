import type { ReactNode } from 'react';

export type IconName =
  | 'search'
  | 'location'
  | 'arrow'
  | 'check'
  | 'shield'
  | 'match'
  | 'contract'
  | 'support'
  | 'menu'
  | 'close'
  | 'book'
  | 'eye'
  | 'eyeOff'
  | 'bell'
  | 'dashboard'
  | 'calendar'
  | 'wallet'
  | 'history'
  | 'send'
  | 'refund'
  | 'award'
  | 'user'
  | 'home'
  | 'logout';

interface IconProps {
  name: IconName;
  size?: number;
}

const paths: Record<IconName, ReactNode> = {
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-4-4" />
    </>
  ),
  location: (
    <>
      <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
      <circle cx="12" cy="10" r="2.5" />
    </>
  ),
  arrow: (
    <>
      <path d="M5 12h14M13 6l6 6-6 6" />
    </>
  ),
  check: <path d="m5 12 4 4L19 6" />,
  shield: (
    <>
      <path d="M12 3 5 6v5c0 4.6 2.8 8.1 7 10 4.2-1.9 7-5.4 7-10V6l-7-3Z" />
      <path d="m9 12 2 2 4-5" />
    </>
  ),
  match: (
    <>
      <circle cx="9" cy="9" r="4" />
      <path d="m12 12 4 4M15 8h5M17.5 5.5v5" />
      <path d="M4 20c.8-3 2.5-4.5 5-4.5 1.4 0 2.6.4 3.5 1.3" />
    </>
  ),
  contract: (
    <>
      <path d="M6 3h9l4 4v14H6Z" />
      <path d="M14 3v5h5M9 13h7M9 17h5" />
    </>
  ),
  support: (
    <>
      <path d="M4 13v-2a8 8 0 0 1 16 0v2" />
      <path d="M4 12H3a2 2 0 0 0-2 2v2a2 2 0 0 0 2 2h2v-6ZM20 12h1a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2h-2v-6ZM19 18c-1 2-2.7 3-5 3" />
    </>
  ),
  menu: (
    <>
      <path d="M4 7h16M4 12h16M4 17h16" />
    </>
  ),
  close: (
    <>
      <path d="m6 6 12 12M18 6 6 18" />
    </>
  ),
  book: (
    <>
      <path d="M4 5.5A3.5 3.5 0 0 1 7.5 2H12v17H7.5A3.5 3.5 0 0 0 4 22Z" />
      <path d="M20 5.5A3.5 3.5 0 0 0 16.5 2H12v17h4.5A3.5 3.5 0 0 1 20 22Z" />
    </>
  ),
  eye: (
    <>
      <path d="M2.5 12s3.4-5 9.5-5 9.5 5 9.5 5-3.4 5-9.5 5-9.5-5-9.5-5Z" />
      <circle cx="12" cy="12" r="2.2" />
    </>
  ),
  eyeOff: (
    <>
      <path d="m3 3 18 18" />
      <path d="M10.6 6.2A10.9 10.9 0 0 1 12 6c6.1 0 9.5 6 9.5 6a17.5 17.5 0 0 1-3.1 3.4M6.2 6.8C3.9 8.3 2.5 12 2.5 12s3.4 6 9.5 6c1.4 0 2.6-.3 3.7-.8" />
    </>
  ),
  bell: (
    <>
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9Z" />
      <path d="M10 21h4" />
    </>
  ),
  dashboard: (
    <>
      <rect x="3" y="3" width="8" height="8" rx="1" />
      <rect x="13" y="3" width="8" height="5" rx="1" />
      <rect x="13" y="10" width="8" height="11" rx="1" />
      <rect x="3" y="13" width="8" height="8" rx="1" />
    </>
  ),
  calendar: (
    <>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M7 3v4M17 3v4M3 10h18M8 14h2M14 14h2M8 18h2" />
    </>
  ),
  wallet: (
    <>
      <rect x="3" y="6" width="18" height="15" rx="2" />
      <path d="M3 9h18M16 15h2M6 6V4h12" />
    </>
  ),
  history: (
    <>
      <path d="M3 12a9 9 0 1 0 2.6-6.4L3 8" />
      <path d="M3 3v5h5M12 7v5l3 2" />
    </>
  ),
  send: (
    <>
      <path d="m22 2-7 20-4-9-9-4Z" />
      <path d="M22 2 11 13" />
    </>
  ),
  refund: (
    <>
      <path d="M3 12a9 9 0 1 0 2.6-6.4L3 8" />
      <path d="M3 3v5h5M12 8v4l3 2" />
    </>
  ),
  award: (
    <>
      <circle cx="12" cy="8" r="5" />
      <path d="m8.5 12-1 9 4.5-2.5 4.5 2.5-1-9" />
      <path d="m10 8 1.4 1.4L14.5 6" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21a8 8 0 0 1 16 0" />
    </>
  ),
  home: (
    <>
      <path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7h-6v7H4a1 1 0 0 1-1-1Z" />
    </>
  ),
  logout: (
    <>
      <path d="M10 17l5-5-5-5M15 12H3" />
      <path d="M12 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6" />
    </>
  )
};

export function Icon({ name, size = 20 }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name]}
    </svg>
  );
}

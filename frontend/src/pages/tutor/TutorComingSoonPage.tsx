import { Link, useParams } from 'react-router-dom';

const copy: Record<string, { title: string; description: string }> = {
  'teaching-requests': {
    title: 'My Teaching Requests',
    description: 'Manage the teaching opportunities you have posted for students.'
  },
  contracts: { title: 'Contracts', description: 'Review contract details and manage your agreements with students.' },
  calendar: { title: 'Calendar', description: 'See your teaching schedule and plan upcoming sessions.' },
  notifications: {
    title: 'Notifications',
    description: 'Keep up with proposal updates, lessons, and account activity.'
  },
  wallet: { title: 'Wallet', description: 'Review your tutor balance and wallet transactions.' }
};

export function TutorComingSoonPage() {
  const { section = '' } = useParams();
  const page = copy[section] ?? {
    title: 'Tutor workspace',
    description: 'This workspace section will be available soon.'
  };
  return (
    <section className="tutor-panel tutor-coming-soon">
      <span className="tutor-eyebrow">TUTOR WORKSPACE</span>
      <h1>{page.title}</h1>
      <p>{page.description}</p>
      <span className="tutor-coming-badge">Coming soon</span>
      <Link className="tutor-detail-link" to="/tutor/dashboard">
        ← Back to dashboard
      </Link>
    </section>
  );
}

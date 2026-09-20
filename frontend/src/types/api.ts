export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  errors: Record<string, string>;
  timestamp: string;
  path: string;
}

export interface Subject {
  id: string;
  name: string;
  description: string | null;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
  updatedAt: string;
}

export type SubjectSort = 'name' | 'popular';

export interface Grade {
  id: string;
  name: string;
  level: number;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
  updatedAt: string;
}

export interface Reference {
  id: string;
  name: string;
}

export interface Availability {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
}

export type TeachingMode = 'ONLINE' | 'OFFLINE';

export interface TeachingRequest {
  id: string;
  tutorId: string;
  tutorName: string;
  tutorAvatarUrl: string | null;
  subjectId: string | null;
  subjectName: string | null;
  customSubjectName: string | null;
  grades: Reference[];
  districts: Reference[];
  title: string | null;
  note: string | null;
  quantity: number | null;
  detailAddress: string | null;
  expectedPrice: number | null;
  teachingMode: TeachingMode;
  preferredSchedule: string | null;
  description: string | null;
  availabilities: Availability[];
  createdAt: string;
  updatedAt: string;
}

export interface PublicReview {
  displayName: string;
  tutorName: string;
  tutorAvatarUrl: string | null;
  rating: number | null;
  comment: string | null;
  createdAt: string;
}

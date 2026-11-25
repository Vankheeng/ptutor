import { useMemo } from 'react';
import type { Contract } from '../../services/studentApi';

export function useComplaintParties(
  contract: Contract | null,
  mode: 'submitted' | 'received',
  role: 'student' | 'tutor'
) {
  return useMemo(() => {
    const tutorName = contract
      ? [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || contract.tutorEmail
      : 'Chưa cập nhật';
    const studentName = contract
      ? [contract.studentFirstName, contract.studentLastName].filter(Boolean).join(' ') || contract.studentEmail
      : 'Chưa cập nhật';
    const currentRole = role === 'tutor' ? 'Gia sư' : 'Học viên';
    const otherName = role === 'tutor' ? studentName : tutorName;
    return {
      senderName: mode === 'submitted' ? `Bạn (${currentRole})` : otherName,
      respondentName: mode === 'submitted' ? otherName : `Bạn (${currentRole})`,
      respondentRole: role === 'tutor' ? 'Học viên' : 'Gia sư'
    };
  }, [contract, mode, role]);
}

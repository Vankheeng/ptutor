import { useState } from 'react';
import { confirmContractLesson } from '../services/refundApi';
import { createTutorLesson, setTutorLessonStatus, updateTutorLesson } from '../services/tutorApi';
import { refundErrorMessage } from '../components/refund/refundFormat';
import type { CalendarEvent, CalendarRole } from './useLessonCalendarData';

type FormValues = { title: string; date: string; startTime: string; endTime: string; note?: string };

export function useLessonCalendarActions(
  role: CalendarRole,
  onNotice: (message: string) => void,
  onRefresh: () => void,
  onDismiss: () => void
) {
  const [busy, setBusy] = useState('');
  const [createError, setCreateError] = useState('');
  const [actionError, setActionError] = useState('');

  const confirm = async (event: CalendarEvent) => {
    setBusy(event.id);
    try {
      await confirmContractLesson(event.id);
      onNotice('Đã xác nhận buổi học.');
      onDismiss();
      onRefresh();
    } catch (failure) {
      onNotice(refundErrorMessage(failure));
    } finally {
      setBusy('');
    }
  };

  const markTaught = async (event: CalendarEvent) => {
    setBusy(event.id);
    try {
      await setTutorLessonStatus(event.id, 'PENDING_CONFIRMATION');
      onNotice('Đã gửi xác nhận buổi học cho học viên.');
      onDismiss();
      onRefresh();
    } catch {
      onNotice('Không thể cập nhật trạng thái buổi học.');
    } finally {
      setBusy('');
    }
  };

  const create = async (form: FormData, hasContracts: boolean) => {
    if (role !== 'TUTOR' || !hasContracts) return;
    setCreateError('');
    try {
      await createTutorLesson(String(form.get('contractId')), {
        title: String(form.get('title')),
        date: String(form.get('date')),
        startTime: String(form.get('startTime')),
        endTime: String(form.get('endTime'))
      });
      onNotice('Đã thêm buổi học.');
      onRefresh();
      return true;
    } catch (failure) {
      setCreateError(failure instanceof Error ? failure.message : 'Không thể lưu buổi học. Vui lòng thử lại.');
      return false;
    }
  };

  const saveSchedule = async (form: FormData, event: CalendarEvent) => {
    setActionError('');
    try {
      const values: FormValues = {
        title: String(form.get('title')),
        date: String(form.get('date')),
        startTime: String(form.get('startTime')),
        endTime: String(form.get('endTime')),
        note: String(form.get('note') ?? '')
      };
      await updateTutorLesson(event.id, values);
      onDismiss();
      onRefresh();
      onNotice('Đã cập nhật lịch học.');
      return true;
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : 'Không thể cập nhật lịch học.');
      return false;
    }
  };

  const cancelSchedule = async (event: CalendarEvent) => {
    setActionError('');
    try {
      await setTutorLessonStatus(event.id, 'CANCELLED');
      onDismiss();
      onRefresh();
      onNotice('Đã hủy buổi học.');
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : 'Không thể hủy buổi học.');
    }
  };

  return { busy, createError, actionError, setActionError, confirm, markTaught, create, saveSchedule, cancelSchedule };
}

import { useEffect, useState } from 'react';
import { ApiError } from '../services/httpClient';
import {
  getStudyingRequest,
  getStudyingRequests,
  type PageResponse,
  type StudyingRequestSearchFilters,
  type StudyingRequestSearchResponse
} from '../services/studyingRequestApi';

interface QueryState<T> {
  key: string;
  data: T | null;
  error: string;
  isLoading: boolean;
}

export function useStudyingRequests(filters: StudyingRequestSearchFilters = {}) {
  const filterKey = JSON.stringify(filters);
  const [state, setState] = useState<QueryState<PageResponse<StudyingRequestSearchResponse>>>({
    key: '',
    data: null,
    error: '',
    isLoading: true
  });

  useEffect(() => {
    const controller = new AbortController();
    getStudyingRequests(JSON.parse(filterKey) as StudyingRequestSearchFilters, controller.signal)
      .then((data) => setState({ key: filterKey, data, error: '', isLoading: false }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted)
          setState({
            key: filterKey,
            data: null,
            error:
              error instanceof ApiError && error.status === 404
                ? 'Máy chủ chưa hỗ trợ danh sách tin tìm gia sư.'
                : error instanceof Error
                  ? error.message
                  : 'Không tải được danh sách tin tìm gia sư.',
            isLoading: false
          });
      });
    return () => controller.abort();
  }, [filterKey]);

  return state.key === filterKey ? state : { data: null, error: '', isLoading: true };
}

export function useStudyingRequest(id: string) {
  const [state, setState] = useState<QueryState<StudyingRequestSearchResponse>>({
    key: '',
    data: null,
    error: '',
    isLoading: true
  });
  useEffect(() => {
    const controller = new AbortController();
    if (!id) return () => controller.abort();
    getStudyingRequest(id, controller.signal)
      .then((data) => setState({ key: id, data, error: '', isLoading: false }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted)
          setState({
            key: id,
            data: null,
            error: error instanceof Error ? error.message : 'Không tải được tin này.',
            isLoading: false
          });
      });
    return () => controller.abort();
  }, [id]);
  if (!id) return { data: null, error: 'Không tìm thấy tin tìm gia sư.', isLoading: false };
  return state.key === id ? state : { data: null, error: '', isLoading: true };
}

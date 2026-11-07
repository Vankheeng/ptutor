import { useEffect, useState } from 'react';
import { getMyTeachingRequests } from '../services/myTeachingRequestApi';

export function useMyTeachingRequests() {
  const [refreshKey, setRefreshKey] = useState(0);
  const [state, setState] = useState<{
    data: Awaited<ReturnType<typeof getMyTeachingRequests>> | null;
    error: string;
    isLoading: boolean;
  }>({ data: null, error: '', isLoading: true });
  useEffect(() => {
    const controller = new AbortController();
    getMyTeachingRequests(controller.signal)
      .then((data) => setState({ data, error: '', isLoading: false }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted)
          setState({
            data: null,
            error: error instanceof Error ? error.message : 'Không tải được yêu cầu dạy.',
            isLoading: false
          });
      });
    return () => controller.abort();
  }, [refreshKey]);
  return { ...state, reload: () => setRefreshKey((value) => value + 1) };
}

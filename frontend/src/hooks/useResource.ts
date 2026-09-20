import { useEffect, useState } from 'react';
import type { AsyncState } from '../types/async';

type ResourceLoader<T> = (signal: AbortSignal) => Promise<T>;

export function useResource<T>(load: ResourceLoader<T>): AsyncState<T> {
  const [state, setState] = useState<AsyncState<T>>({ data: null, error: null, isLoading: true });

  useEffect(() => {
    const controller = new AbortController();

    load(controller.signal)
      .then((data) => setState({ data, error: null, isLoading: false }))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        setState({
          data: null,
          error: error instanceof Error ? error.message : 'Không thể tải dữ liệu.',
          isLoading: false
        });
      });

    return () => controller.abort();
  }, [load]);

  return state;
}

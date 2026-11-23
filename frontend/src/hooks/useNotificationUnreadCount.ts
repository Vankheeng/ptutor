import { useEffect, useState } from 'react';
import { getUnreadCount } from '../services/studentApi';

const POLL_INTERVAL = 15_000;

export function useNotificationUnreadCount() {
  const [count, setCount] = useState(0);

  useEffect(() => {
    let active = true;
    const load = () => {
      const controller = new AbortController();
      getUnreadCount(controller.signal)
        .then((result) => {
          if (active) setCount(result.unreadCount);
        })
        .catch(() => undefined);
      return controller;
    };
    let controller = load();
    const timer = window.setInterval(() => {
      controller.abort();
      controller = load();
    }, POLL_INTERVAL);
    return () => {
      active = false;
      controller.abort();
      window.clearInterval(timer);
    };
  }, []);

  return count;
}

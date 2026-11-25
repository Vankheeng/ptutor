import { useCallback, useEffect, useState } from 'react';

const VISIBILITY_OFFSET = 360;

export function useScrollToTop() {
  const [isVisible, setIsVisible] = useState(() => window.scrollY > VISIBILITY_OFFSET);

  useEffect(() => {
    const updateVisibility = () => setIsVisible(window.scrollY > VISIBILITY_OFFSET);
    window.addEventListener('scroll', updateVisibility, { passive: true });
    return () => window.removeEventListener('scroll', updateVisibility);
  }, []);

  const scrollToTop = useCallback(() => {
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    window.scrollTo({ top: 0, behavior: reducedMotion ? 'auto' : 'smooth' });
  }, []);

  return { isVisible, scrollToTop };
}

import { useEffect } from 'react';

const REVEAL_SELECTORS = [
  '.hero-copy',
  '.hero-visual',
  '.section-heading',
  '.subject-card',
  '.teaching-request-card',
  '.feature-visual',
  '.feature-copy',
  '.testimonial-card',
  '.cta-box',
  '.site-footer'
].join(', ');

export function useHomeReveal(): void {
  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    const elements = Array.from(document.querySelectorAll<HTMLElement>(REVEAL_SELECTORS));
    elements.forEach((element, index) => {
      element.classList.add('scroll-reveal');
      element.style.setProperty('--reveal-delay', `${(index % 6) * 65}ms`);
    });

    const observer = new IntersectionObserver(
      (entries) =>
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        }),
      { threshold: 0.12, rootMargin: '0px 0px -36px' }
    );

    elements.forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  }, []);
}

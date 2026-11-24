import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import './global-toast.css';
type ToastType = 'success' | 'error';
type ToastItem = { id: number; message: string; type: ToastType };

function normalizeToastMessage(message: string) {
  if (/A lesson can only be marked as taught after it ends/i.test(message)) {
    return 'Chỉ có thể đánh dấu buổi học đã dạy sau khi buổi học kết thúc.';
  }
  if (/A lesson can only be confirmed after it ends/i.test(message)) {
    return 'Chỉ có thể xác nhận buổi học sau khi buổi học kết thúc.';
  }
  if (/The related tuition installment must be paid before this lesson can be marked as taught/i.test(message)) {
    return 'Kỳ học phí liên quan phải được thanh toán trước khi đánh dấu buổi học đã dạy.';
  }
  if (message.length >= 40) return message;
  return /trùng lịch|lịch(?: học| của (?:gia sư|học viên))?.*bị trùng/i.test(message) ? 'Lịch học bị trùng' : message;
}

const NOTICE_CLASSES = /(?:^|\s)[\w-]*(?:notice|error|alert|message|success)[\w-]*(?:\s|$)/i;

export function GlobalToastViewport() {
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(0);

  useEffect(() => {
    const seen = new WeakMap<Element, string>();
    const recent = new Map<string, number>();
    const timers = new Set<number>();
    let frame = 0;

    const scan = () => {
      frame = 0;
      document.querySelectorAll<HTMLElement>('[role="alert"], [role="status"]').forEach((element) => {
        if (
          element.closest('.global-toast-viewport') ||
          element.matches('.tutor-review-pending, .tutor-lesson-edit-error')
        )
          return;
        const role = element.getAttribute('role');
        if (role === 'status' && !NOTICE_CLASSES.test(element.className)) return;
        // Inline notices are intentionally hidden from the page layout, but
        // they still need to be promoted to the corner toast.
        if (element.getClientRects().length === 0 && !NOTICE_CLASSES.test(element.className)) return;

        const rawMessage = element.textContent?.replace(/\s+/g, ' ').trim();
        const message = rawMessage ? normalizeToastMessage(rawMessage) : '';
        if (!message || message === seen.get(element)) return;
        seen.set(element, message);

        const type: ToastType = role === 'alert' || /(?:error|danger)/i.test(element.className) ? 'error' : 'success';
        const key = `${type}:${message}`;
        const now = Date.now();
        if (now - (recent.get(key) ?? 0) < 3000) return;
        recent.set(key, now);

        const id = ++nextId.current;
        setItems((current) => [...current.slice(-2), { id, message, type }]);
        const timer = window.setTimeout(() => {
          setItems((current) => current.filter((item) => item.id !== id));
          timers.delete(timer);
        }, 6000);
        timers.add(timer);
      });
    };

    const scheduleScan = () => {
      if (!frame) frame = window.requestAnimationFrame(scan);
    };
    const onToast = (event: Event) => {
      const detail = (event as CustomEvent<{ message?: string; type?: ToastType }>).detail;
      if (!detail?.message) return;
      const id = ++nextId.current;
      setItems((current) => [
        ...current.slice(-2),
        { id, message: normalizeToastMessage(detail.message!), type: detail.type ?? 'success' }
      ]);
      window.setTimeout(() => setItems((current) => current.filter((item) => item.id !== id)), 6000);
    };
    const observer = new MutationObserver(scheduleScan);
    observer.observe(document.body, {
      childList: true,
      subtree: true,
      characterData: true,
      attributes: true,
      attributeFilter: ['role', 'class']
    });
    window.addEventListener('ptutor:toast', onToast);
    scheduleScan();

    return () => {
      observer.disconnect();
      window.removeEventListener('ptutor:toast', onToast);
      if (frame) window.cancelAnimationFrame(frame);
      timers.forEach((timer) => window.clearTimeout(timer));
    };
  }, []);

  return createPortal(
    <div className="global-toast-viewport">
      {items.map((item) => (
        <div key={item.id} className={`global-toast global-toast-${item.type}`}>
          <span>{item.message}</span>
          <button
            type="button"
            aria-label="Đóng thông báo"
            onClick={() => setItems((current) => current.filter((toast) => toast.id !== item.id))}
          >
            ×
          </button>
        </div>
      ))}
    </div>,
    document.body
  );
}

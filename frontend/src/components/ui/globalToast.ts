export type ToastType = 'success' | 'error';

export function notifyGlobalToast(message: string, type: ToastType = 'success') {
  window.dispatchEvent(new CustomEvent('ptutor:toast', { detail: { message, type } }));
}

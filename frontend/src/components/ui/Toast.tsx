interface ToastProps {
  message: string;
  type?: 'success' | 'error';
}

export function Toast({ message, type = 'success' }: ToastProps) {
  return (
    <div className={`app-toast app-toast-${type}`} role="status">
      {message}
    </div>
  );
}

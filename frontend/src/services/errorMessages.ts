import { ApiError } from './httpClient';
import { MESSAGES } from './errorCodes';

/** A Vietnamese message for an error: by its code when we know it, else {@code fallback}. */
export function errorMessage(caught: unknown, fallback: string): string {
  if (caught instanceof ApiError) return MESSAGES[caught.code] ?? fallback;
  // fetch itself fails (TypeError) when the server cannot be reached.
  if (caught instanceof TypeError) return 'Không kết nối được tới máy chủ. Vui lòng kiểm tra mạng.';
  return fallback;
}

/** The Vietnamese text of a code, or undefined when there is none. */
export function messageForCode(code: string): string | undefined {
  return MESSAGES[code];
}

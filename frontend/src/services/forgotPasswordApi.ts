import { post } from './httpClient';

export interface OtpVerificationResponse {
  valid: boolean;
}

export function sendPasswordResetOtp(email: string): Promise<void> {
  return post<void>('/api/v1/auth/password-reset/otp', { email }, { skipAuthRefresh: true });
}

export function verifyPasswordResetOtp(email: string, otp: string): Promise<OtpVerificationResponse> {
  return post<OtpVerificationResponse>('/api/v1/auth/password-reset/verify', { email, otp }, { skipAuthRefresh: true });
}

export function resetPassword(
  email: string,
  otp: string,
  newPassword: string,
  confirmNewPassword: string
): Promise<void> {
  return post<void>(
    '/api/v1/auth/password-reset/reset',
    { email, otp, newPassword, confirmNewPassword },
    { skipAuthRefresh: true }
  );
}

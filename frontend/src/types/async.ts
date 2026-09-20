export interface AsyncState<T> {
  data: T | null;
  error: string | null;
  isLoading: boolean;
}

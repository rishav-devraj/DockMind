/** The server answered with a non-2xx status and the contract's error body. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

/** The request never produced a usable answer: no signal, timeout, or a broken response. */
export class NetworkError extends Error {
  readonly timedOut: boolean;

  constructor(message: string, timedOut = false) {
    super(message);
    this.name = 'NetworkError';
    this.timedOut = timedOut;
  }
}

/** Text that a person on a loading dock can act on. */
export function plainMessage(err: unknown): string {
  if (err instanceof NetworkError) {
    return err.timedOut
      ? 'The server took too long to answer. Try again.'
      : 'Could not reach the server. Check the signal and try again.';
  }
  if (err instanceof ApiError) {
    if (err.status >= 500) return 'The server had a problem. Try again in a moment.';
    return err.message;
  }
  return 'Something went wrong. Try again.';
}

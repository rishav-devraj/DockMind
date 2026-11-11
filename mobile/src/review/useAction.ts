import { useCallback, useRef, useState } from 'react';

import { ApiError, plainMessage } from '../api/errors';

/**
 * Runs one server action at a time. The ref blocks a second tap in the same frame,
 * before the disabled state has rendered.
 */
export function useAction(onConflict?: () => void) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inFlight = useRef(false);

  const run = useCallback(
    async (action: () => Promise<void>) => {
      if (inFlight.current) return;
      inFlight.current = true;
      setBusy(true);
      setError(null);
      try {
        await action();
      } catch (caught) {
        setError(plainMessage(caught));
        // Someone else changed this receipt, so what is on screen is out of date.
        if (caught instanceof ApiError && caught.status === 409) onConflict?.();
      } finally {
        inFlight.current = false;
        setBusy(false);
      }
    },
    [onConflict],
  );

  return { run, busy, error, setError };
}

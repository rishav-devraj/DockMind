import { useCallback, useEffect, useState } from 'react';

import { ApiError, plainMessage } from '../api/errors';
import type { Receipt } from '../api/types';
import { api } from '../lib/runtime';

const POLL_MS = 2_000;
const SLOW_AFTER_MS = 6_000;

/** Loads a receipt and keeps polling while the server is still reading the note. */
export function useReceipt(receiptId: string | null) {
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [slow, setSlow] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!receiptId) return;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const startedAt = Date.now();

    const poll = async () => {
      try {
        const latest = await api.getReceipt(receiptId);
        if (cancelled) return;
        setReceipt(latest);
        setError(null);
        if (latest.status !== 'CAPTURED') return;
      } catch (caught) {
        if (cancelled) return;
        setError(plainMessage(caught));
        // A 4xx will not change by asking again. Network trouble and 5xx might.
        if (caught instanceof ApiError && caught.status < 500) return;
      }
      if (Date.now() - startedAt >= SLOW_AFTER_MS) setSlow(true);
      timer = setTimeout(poll, POLL_MS);
    };
    void poll();

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [receiptId, attempt]);

  const retry = useCallback(() => {
    setError(null);
    setSlow(false);
    setAttempt((value) => value + 1);
  }, []);

  return { receipt, setReceipt, error, slow, retry };
}

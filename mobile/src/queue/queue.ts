import { ApiError, plainMessage } from '../api/errors';
import type { Fixture } from '../api/types';

export type QueueStatus = 'WAITING' | 'UPLOADING' | 'SYNCED' | 'FAILED';

export interface QueueItem {
  id: number;
  idempotencyKey: string;
  photoUri: string;
  fixture: Fixture | null;
  status: QueueStatus;
  attempts: number;
  nextAttemptAt: number;
  lastError: string | null;
  receiptId: string | null;
  createdAt: number;
}

export type NewQueueItem = Pick<QueueItem, 'idempotencyKey' | 'photoUri' | 'fixture'>;

export type QueuePatch = Partial<
  Pick<QueueItem, 'status' | 'attempts' | 'nextAttemptAt' | 'lastError' | 'receiptId'>
>;

export interface QueueStore {
  insert(item: NewQueueItem, createdAt: number): Promise<QueueItem>;
  /** All items, oldest first. */
  list(): Promise<QueueItem[]>;
  update(id: number, patch: QueuePatch): Promise<void>;
}

export type UploadOutcome = { kind: 'retry'; reason: string } | { kind: 'fail'; reason: string };

export interface QueueDeps {
  store: QueueStore;
  /** Uploads one item and resolves with the server's receipt id. */
  upload: (item: QueueItem) => Promise<string>;
  classify?: (error: unknown) => UploadOutcome;
  isOnline: () => boolean;
  now: () => number;
  /** Runs fn after ms and returns a cancel function. */
  setTimer: (fn: () => void, ms: number) => () => void;
  baseDelayMs?: number;
  maxDelayMs?: number;
}

/** Network errors, timeouts and 5xx retry. A request the server rejects will never succeed. */
export function classifyUploadError(error: unknown): UploadOutcome {
  if (error instanceof ApiError) {
    const transient = error.status >= 500 || error.status === 408 || error.status === 429;
    if (!transient) {
      const reason = error.status === 413 ? 'This photo is too large to upload.' : error.message;
      return { kind: 'fail', reason };
    }
  }
  return { kind: 'retry', reason: plainMessage(error) };
}

export class UploadQueue {
  private readonly deps: QueueDeps;
  private readonly classify: (error: unknown) => UploadOutcome;
  private readonly baseDelayMs: number;
  private readonly maxDelayMs: number;
  private readonly listeners = new Set<() => void>();
  private items: QueueItem[] = [];
  private started?: Promise<void>;
  private running: Promise<void> | null = null;
  private rerun = false;
  private cancelTimer: (() => void) | null = null;

  constructor(deps: QueueDeps) {
    this.deps = deps;
    this.classify = deps.classify ?? classifyUploadError;
    this.baseDelayMs = deps.baseDelayMs ?? 2_000;
    this.maxDelayMs = deps.maxDelayMs ?? 60_000;
  }

  /** Runs once per process, so it can never reset an upload that is really in flight. */
  start(): Promise<void> {
    this.started ??= this.recover();
    return this.started;
  }

  async enqueue(item: NewQueueItem): Promise<QueueItem> {
    await this.start();
    const created = await this.deps.store.insert(item, this.deps.now());
    await this.publish();
    void this.drain();
    return created;
  }

  /** The clerk's manual retry of a FAILED item. The key stays the same. */
  async retry(id: number): Promise<void> {
    await this.start();
    const item = (await this.deps.store.list()).find((candidate) => candidate.id === id);
    if (item?.status !== 'FAILED') return;
    await this.deps.store.update(id, {
      status: 'WAITING',
      attempts: 0,
      nextAttemptAt: 0,
      lastError: null,
    });
    await this.publish();
    void this.drain();
  }

  /** Signal came back or the app returned to the foreground, so skip any backoff and try now. */
  async wake(): Promise<void> {
    await this.start();
    const items = await this.deps.store.list();
    for (const item of items) {
      if (item.status === 'WAITING' && item.nextAttemptAt > 0) {
        await this.deps.store.update(item.id, { nextAttemptAt: 0 });
      }
    }
    await this.publish();
    await this.drain();
  }

  /** Single flight: a call during a run asks that run to look again. */
  drain(): Promise<void> {
    if (this.running) {
      this.rerun = true;
      return this.running;
    }
    this.running = this.run();
    return this.running;
  }

  getItems = (): QueueItem[] => this.items;

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  private async recover(): Promise<void> {
    const items = await this.deps.store.list();
    for (const item of items) {
      if (item.status === 'UPLOADING') {
        await this.deps.store.update(item.id, { status: 'WAITING', nextAttemptAt: 0 });
      }
    }
    await this.publish();
    void this.drain();
  }

  private async run(): Promise<void> {
    try {
      do {
        this.rerun = false;
        await this.pump();
      } while (this.rerun);
    } catch (error) {
      // Local storage failed. Look again later rather than stopping for good.
      console.warn('Upload queue stopped', error);
      this.schedule(this.maxDelayMs);
    } finally {
      this.running = null;
    }
  }

  private async pump(): Promise<void> {
    const { store, upload, isOnline, now } = this.deps;
    this.clearTimer();

    while (isOnline()) {
      const head = (await store.list()).find((item) => item.status === 'WAITING');
      if (!head) return;

      // Strict order: a newer item never overtakes one that is backing off.
      const wait = head.nextAttemptAt - now();
      if (wait > 0) {
        this.schedule(wait);
        return;
      }

      const attempts = head.attempts + 1;
      await store.update(head.id, { status: 'UPLOADING', attempts });
      await this.publish();

      try {
        const receiptId = await upload(head);
        await store.update(head.id, { status: 'SYNCED', receiptId, lastError: null });
      } catch (error) {
        const outcome = this.classify(error);
        await store.update(
          head.id,
          outcome.kind === 'retry'
            ? {
                status: 'WAITING',
                nextAttemptAt: now() + this.backoff(attempts),
                lastError: outcome.reason,
              }
            : { status: 'FAILED', lastError: outcome.reason },
        );
      }
      await this.publish();
    }
  }

  private backoff(attempts: number): number {
    return Math.min(this.maxDelayMs, this.baseDelayMs * 2 ** (attempts - 1));
  }

  private schedule(ms: number): void {
    this.clearTimer();
    this.cancelTimer = this.deps.setTimer(() => {
      this.cancelTimer = null;
      void this.drain();
    }, ms);
  }

  private clearTimer(): void {
    this.cancelTimer?.();
    this.cancelTimer = null;
  }

  private async publish(): Promise<void> {
    this.items = await this.deps.store.list();
    this.listeners.forEach((listener) => listener());
  }
}

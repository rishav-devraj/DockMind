import { ApiError, NetworkError } from '../api/errors';
import {
  NewQueueItem,
  QueueItem,
  QueuePatch,
  QueueStore,
  UploadQueue,
} from './queue';

class MemoryStore implements QueueStore {
  items: QueueItem[] = [];
  private nextId = 1;

  async insert(item: NewQueueItem, createdAt: number) {
    const row: QueueItem = {
      ...item,
      id: this.nextId++,
      status: 'WAITING',
      attempts: 0,
      nextAttemptAt: 0,
      lastError: null,
      receiptId: null,
      createdAt,
    };
    this.items.push(row);
    return { ...row };
  }

  async list() {
    return this.items.map((item) => ({ ...item }));
  }

  async update(id: number, patch: QueuePatch) {
    Object.assign(this.items.find((item) => item.id === id)!, patch);
  }

  async remove(id: number) {
    this.items = this.items.filter((item) => item.id !== id);
  }
}

function harness(store = new MemoryStore()) {
  let time = 1_000;
  let online = true;
  let timer: { fn: () => void; at: number } | null = null;
  const calls: string[] = [];
  let behaviour: (item: QueueItem) => Promise<string> = async (item) => `RCP-${item.id}`;

  const queue = new UploadQueue({
    store,
    now: () => time,
    isOnline: () => online,
    upload: (item) => {
      calls.push(item.idempotencyKey);
      return behaviour(item);
    },
    setTimer: (fn, ms) => {
      timer = { fn, at: time + ms };
      return () => {
        timer = null;
      };
    },
    baseDelayMs: 1_000,
    maxDelayMs: 8_000,
  });

  return {
    queue,
    store,
    calls,
    setOnline: (value: boolean) => (online = value),
    setUpload: (fn: typeof behaviour) => (behaviour = fn),
    timerAt: () => timer?.at ?? null,
    /** Moves the clock to the pending timer and lets the drain it starts finish. */
    fireTimer: async () => {
      const pending = timer!;
      timer = null;
      time = pending.at;
      pending.fn();
      await queue.drain();
    },
  };
}

const item = (key: string): NewQueueItem => ({
  idempotencyKey: key,
  photoUri: `file:///${key}.jpg`,
  fixture: null,
});

describe('UploadQueue', () => {
  it('uploads in capture order, one at a time', async () => {
    const h = harness();
    h.setOnline(false);
    await h.queue.enqueue(item('a'));
    await h.queue.enqueue(item('b'));
    await h.queue.enqueue(item('c'));
    expect(h.calls).toEqual([]);

    let active = 0;
    let maxActive = 0;
    h.setUpload(async (row) => {
      maxActive = Math.max(maxActive, ++active);
      await Promise.resolve();
      active--;
      return `RCP-${row.id}`;
    });
    h.setOnline(true);
    await h.queue.wake();

    expect(h.calls).toEqual(['a', 'b', 'c']);
    expect(maxActive).toBe(1);
    expect(h.store.items.map((row) => row.status)).toEqual(['SYNCED', 'SYNCED', 'SYNCED']);
    expect(h.store.items[1].receiptId).toBe('RCP-2');
  });

  it('keeps the same key on retry and backs off exponentially', async () => {
    const h = harness();
    h.setUpload(async () => {
      throw new NetworkError('down');
    });
    await h.queue.enqueue(item('a'));
    await h.queue.drain();

    expect(h.store.items[0]).toMatchObject({ status: 'WAITING', attempts: 1 });
    expect(h.timerAt()).toBe(2_000);

    await h.fireTimer();
    expect(h.timerAt()).toBe(4_000);

    h.setUpload(async () => 'RCP-9');
    await h.fireTimer();
    expect(h.calls).toEqual(['a', 'a', 'a']);
    expect(h.store.items[0]).toMatchObject({ status: 'SYNCED', receiptId: 'RCP-9', lastError: null });
  });

  it('does not let a newer item overtake one that is backing off', async () => {
    const h = harness();
    h.setUpload(async () => {
      throw new ApiError(503, 'DOWN', 'unavailable');
    });
    await h.queue.enqueue(item('a'));
    await h.queue.drain();
    await h.queue.enqueue(item('b'));
    await h.queue.drain();

    expect(h.calls).toEqual(['a']);
    expect(h.store.items[1].status).toBe('WAITING');
  });

  it('recovers a row left UPLOADING by an app kill, keeping its key', async () => {
    const store = new MemoryStore();
    const before = harness(store);
    before.setUpload(() => new Promise<string>(() => {})); // Never answers: the app dies here.
    await before.queue.enqueue(item('a'));
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(store.items[0].status).toBe('UPLOADING');

    const after = harness(store);
    await after.queue.start();
    await after.queue.drain();

    expect(after.calls).toEqual(['a']);
    expect(store.items).toHaveLength(1);
    expect(store.items[0]).toMatchObject({ status: 'SYNCED', idempotencyKey: 'a' });
  });

  it.each([
    [400, 'Idempotency-Key is required.', 'Idempotency-Key is required. (HTTP 400)'],
    [413, 'Too big', 'This photo is too large to upload. (HTTP 413)'],
  ])('moves a %i to FAILED with a readable reason', async (status, message, reason) => {
    const h = harness();
    h.setUpload(async () => {
      throw new ApiError(status, 'REJECTED', message);
    });
    await h.queue.enqueue(item('a'));
    await h.queue.drain();

    expect(h.store.items[0]).toMatchObject({ status: 'FAILED', lastError: reason });
    expect(h.timerAt()).toBeNull();
  });

  it('skips a FAILED item so later items still upload', async () => {
    const h = harness();
    h.setUpload(async (row) => {
      if (row.idempotencyKey === 'a') throw new ApiError(400, 'BAD', 'Bad photo');
      return 'RCP-2';
    });
    await h.queue.enqueue(item('a'));
    await h.queue.enqueue(item('b'));
    await h.queue.drain();

    expect(h.store.items.map((row) => row.status)).toEqual(['FAILED', 'SYNCED']);
  });

  it('cancels a WAITING item so it never uploads, and lets later items go', async () => {
    const h = harness();
    h.setUpload(async (row) => {
      if (row.idempotencyKey === 'a') throw new NetworkError('Network request failed');
      return 'RCP-2';
    });
    await h.queue.enqueue(item('a'));
    await h.queue.enqueue(item('b'));
    await h.queue.drain();

    expect(await h.queue.cancel(1)).toBe(true);
    await h.queue.drain();

    expect(h.store.items.map((row) => row.idempotencyKey)).toEqual(['b']);
    expect(h.store.items[0].status).toBe('SYNCED');
    expect(h.calls).toEqual(['a', 'b']);
  });

  it('refuses to cancel a synced item', async () => {
    const h = harness();
    await h.queue.enqueue(item('a'));
    await h.queue.drain();

    expect(await h.queue.cancel(1)).toBe(false);
    expect(h.store.items).toHaveLength(1);
  });

  it('retries a FAILED item by hand with the same key', async () => {
    const h = harness();
    h.setUpload(async () => {
      throw new ApiError(400, 'BAD', 'Bad photo');
    });
    await h.queue.enqueue(item('a'));
    await h.queue.drain();

    h.setUpload(async () => 'RCP-1');
    await h.queue.retry(1);
    await h.queue.drain();

    expect(h.calls).toEqual(['a', 'a']);
    expect(h.store.items[0]).toMatchObject({ status: 'SYNCED', lastError: null });
  });

  it('makes no attempts offline and skips backoff on reconnect', async () => {
    const h = harness();
    h.setUpload(async () => {
      throw new NetworkError('down');
    });
    await h.queue.enqueue(item('a'));
    await h.queue.drain();
    expect(h.timerAt()).toBe(2_000);

    h.setOnline(false);
    await h.queue.wake();
    expect(h.calls).toEqual(['a']);

    h.setUpload(async () => 'RCP-1');
    h.setOnline(true);
    await h.queue.wake();
    expect(h.store.items[0].status).toBe('SYNCED');
  });

  it('notifies subscribers with the latest items', async () => {
    const h = harness();
    const seen: string[] = [];
    h.queue.subscribe(() => seen.push(h.queue.getItems()[0]?.status ?? 'EMPTY'));
    await h.queue.enqueue(item('a'));
    await h.queue.drain();

    expect(seen).toContain('WAITING');
    expect(seen).toContain('UPLOADING');
    expect(seen[seen.length - 1]).toBe('SYNCED');
  });
});

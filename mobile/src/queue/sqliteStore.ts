import type { Fixture } from '../api/types';
import { getDb } from '../lib/db';
import type { NewQueueItem, QueueItem, QueuePatch, QueueStatus, QueueStore } from './queue';

interface Row {
  id: number;
  idempotency_key: string;
  photo_uri: string;
  fixture: string | null;
  status: string;
  attempts: number;
  next_attempt_at: number;
  last_error: string | null;
  receipt_id: string | null;
  created_at: number;
}

const COLUMNS: Record<keyof QueuePatch, string> = {
  status: 'status',
  attempts: 'attempts',
  nextAttemptAt: 'next_attempt_at',
  lastError: 'last_error',
  receiptId: 'receipt_id',
};

const toItem = (row: Row): QueueItem => ({
  id: row.id,
  idempotencyKey: row.idempotency_key,
  photoUri: row.photo_uri,
  fixture: row.fixture as Fixture | null,
  status: row.status as QueueStatus,
  attempts: row.attempts,
  nextAttemptAt: row.next_attempt_at,
  lastError: row.last_error,
  receiptId: row.receipt_id,
  createdAt: row.created_at,
});

export const sqliteQueueStore: QueueStore = {
  async insert(item: NewQueueItem, createdAt: number) {
    const db = await getDb();
    const result = await db.runAsync(
      `INSERT INTO queue_items (idempotency_key, photo_uri, fixture, status, created_at)
       VALUES (?, ?, ?, 'WAITING', ?)`,
      item.idempotencyKey,
      item.photoUri,
      item.fixture,
      createdAt,
    );
    const row = await db.getFirstAsync<Row>(
      'SELECT * FROM queue_items WHERE id = ?',
      result.lastInsertRowId,
    );
    return toItem(row!);
  },

  async list() {
    const db = await getDb();
    const rows = await db.getAllAsync<Row>('SELECT * FROM queue_items ORDER BY id ASC');
    return rows.map(toItem);
  },

  async remove(id: number) {
    const db = await getDb();
    await db.runAsync('DELETE FROM queue_items WHERE id = ?', id);
  },

  async update(id: number, patch: QueuePatch) {
    const entries = Object.entries(patch) as [keyof QueuePatch, string | number | null][];
    if (entries.length === 0) return;
    const assignments = entries.map(([field]) => `${COLUMNS[field]} = ?`).join(', ');
    const db = await getDb();
    await db.runAsync(
      `UPDATE queue_items SET ${assignments} WHERE id = ?`,
      ...entries.map(([, value]) => value),
      id,
    );
  },
};

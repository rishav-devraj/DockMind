import * as Crypto from 'expo-crypto';
import { Directory, File, Paths } from 'expo-file-system';

import type { Fixture } from '../api/types';
import type { QueueItem } from '../queue/queue';
import { uploadQueue } from '../queue/runtime';
import { downscale } from './photo';

/**
 * The single path for every capture, online or offline. The photo and key are on disk
 * before any upload is tried, so a retry after an app kill reuses the same key.
 */
export async function saveCapture(
  asset: { uri: string; width: number; height: number },
  fixture: Fixture | null,
): Promise<QueueItem> {
  const compressedUri = await downscale(asset);

  const idempotencyKey = Crypto.randomUUID();
  const directory = new Directory(Paths.document, 'captures');
  directory.create({ idempotent: true, intermediates: true });
  const stored = new File(directory, `${idempotencyKey}.jpg`);
  await new File(compressedUri).copy(stored);

  return uploadQueue.enqueue({ idempotencyKey, photoUri: stored.uri, fixture });
}

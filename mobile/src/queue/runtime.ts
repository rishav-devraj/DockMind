import { File } from 'expo-file-system';
import { useSyncExternalStore } from 'react';
import { AppState } from 'react-native';

import { ApiError } from '../api/errors';
import { isOnline, subscribeNetwork } from '../lib/network';
import { api } from '../lib/runtime';
import { QueueItem, UploadQueue } from './queue';
import { sqliteQueueStore } from './sqliteStore';

export const uploadQueue = new UploadQueue({
  store: sqliteQueueStore,
  isOnline,
  now: Date.now,
  setTimer: (fn, ms) => {
    const timer = setTimeout(fn, ms);
    return () => clearTimeout(timer);
  },
  upload: async (item: QueueItem) => {
    if (!new File(item.photoUri).exists) {
      throw new ApiError(400, 'MISSING_IMAGE', 'The saved photo is no longer on this phone.');
    }
    const { receipt } = await api.uploadReceipt({
      imageUri: item.photoUri,
      idempotencyKey: item.idempotencyKey,
      fixture: item.fixture,
    });
    return receipt.id;
  },
});

/** Removes a WAITING or FAILED item and its saved photo. Returns false if it could not be removed. */
export async function cancelUpload(item: QueueItem): Promise<boolean> {
  const removed = await uploadQueue.cancel(item.id);
  if (removed) {
    try {
      const file = new File(item.photoUri);
      if (file.exists) file.delete();
    } catch (error) {
      console.warn('Could not delete the saved photo', error);
    }
  }
  return removed;
}

/** Starts draining and keeps doing so on reconnect and on return to the foreground. */
export function startUploadQueue(): () => void {
  const wake = () => {
    uploadQueue.wake().catch((error) => console.warn('Upload queue wake failed', error));
  };
  uploadQueue.start().catch((error) => console.warn('Upload queue start failed', error));

  const stopNetwork = subscribeNetwork(() => {
    if (isOnline()) wake();
  });
  const appState = AppState.addEventListener('change', (state) => {
    if (state === 'active') wake();
  });
  return () => {
    stopNetwork();
    appState.remove();
  };
}

export const useQueueItems = () =>
  useSyncExternalStore(uploadQueue.subscribe, uploadQueue.getItems);

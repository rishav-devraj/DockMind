import { File } from 'expo-file-system';

import { createApiClient } from '../api/client';
import { kvGet, kvSet } from './db';
import { isOnline } from './network';

export const api = createApiClient({
  isOnline,
  imageSize: (uri) => new File(uri).size,
  persistence: {
    load: () => kvGet('mock-state'),
    save: (state) => kvSet('mock-state', state),
  },
});

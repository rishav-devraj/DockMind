import { createApiClient } from '../api/client';
import { kvGet, kvSet } from './db';
import { isOnline } from './network';

export const api = createApiClient({
  isOnline,
  persistence: {
    load: () => kvGet('mock-state'),
    save: (state) => kvSet('mock-state', state),
  },
});

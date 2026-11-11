import NetInfo from '@react-native-community/netinfo';
import { useSyncExternalStore } from 'react';

// Only isConnected is used. isInternetReachable pings a public site, which would report
// "offline" on a dock network that can reach our server but not the internet.
let online = true;
const listeners = new Set<() => void>();

NetInfo.addEventListener((state) => {
  const next = state.isConnected !== false;
  if (next === online) return;
  online = next;
  listeners.forEach((listener) => listener());
});

export const isOnline = () => online;

export function subscribeNetwork(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export const useIsOnline = () => useSyncExternalStore(subscribeNetwork, isOnline);

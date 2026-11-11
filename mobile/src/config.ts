// Expo inlines EXPO_PUBLIC_* at build time, so each must be read by its full literal name.
export const apiMode: 'mock' | 'http' =
  process.env.EXPO_PUBLIC_API_MODE === 'http' ? 'http' : 'mock';

export const apiUrl = process.env.EXPO_PUBLIC_API_URL || 'http://10.0.2.2:8080';

// Expo inlines EXPO_PUBLIC_* at build time, so it must be read by its full literal name.
export const apiUrl = process.env.EXPO_PUBLIC_API_URL || 'https://dockmind-1.onrender.com';

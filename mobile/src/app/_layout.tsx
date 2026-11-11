import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';

import { startUploadQueue } from '@/queue/runtime';

export default function RootLayout() {
  useEffect(() => startUploadQueue(), []);

  return (
    <>
      <StatusBar style="dark" />
      <Stack />
    </>
  );
}

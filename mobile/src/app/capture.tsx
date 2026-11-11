import { router, Stack } from 'expo-router';
import { useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { pickFromGallery, PickResult, takePhoto } from '@/capture/photo';
import { saveCapture } from '@/capture/saveCapture';
import { Screen } from '@/components/Screen';
import { Button, Notice } from '@/components/ui';
import { isOnline } from '@/lib/network';
import { colors, spacing, type } from '@/theme';

export default function Capture() {
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const inFlight = useRef(false);

  const run = async (pick: () => Promise<PickResult>) => {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setProblem(null);
    try {
      const result = await pick();
      if (result.kind === 'denied') setProblem(result.message);
      if (result.kind === 'picked') {
        const item = await saveCapture(result.asset, null);
        // With no signal the clerk may capture several notes, so show the queue instead.
        if (isOnline()) router.replace({ pathname: '/review', params: { itemId: String(item.id) } });
        else router.replace('/queue');
      }
    } catch {
      setProblem('Could not save that photo. Try taking it again.');
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  return (
    <Screen>
      <View style={styles.content}>
        <Stack.Screen options={{ title: 'Capture note' }} />
        <Text style={styles.intro}>Photograph the whole delivery note, flat and in good light.</Text>
        {problem ? <Notice text={problem} /> : null}
        <Button label="Take photo" onPress={() => run(takePhoto)} busy={busy} />
        <Button
          label="Choose from gallery"
          variant="secondary"
          onPress={() => run(pickFromGallery)}
          disabled={busy}
        />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { flex: 1, padding: spacing.lg, gap: spacing.md },
  intro: { fontSize: type.body, color: colors.text },
});

import { Stack } from 'expo-router';
import { useRef, useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';

import { Button, Notice } from '@/components/ui';
import { downscale, pickFromGallery, PickResult, takePhoto } from '@/capture/photo';
import { colors, spacing, type } from '@/theme';

export default function Capture() {
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const inFlight = useRef(false);

  const run = async (pick: () => Promise<PickResult>) => {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setProblem(null);
    try {
      const result = await pick();
      if (result.kind === 'denied') setProblem(result.message);
      if (result.kind === 'picked') setPreview(await downscale(result.asset));
    } catch {
      setProblem('Could not save that photo. Try taking it again.');
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  return (
    <View style={styles.screen}>
      <Stack.Screen options={{ title: 'Capture note' }} />
      <Text style={styles.intro}>Photograph the whole delivery note, flat and in good light.</Text>
      {problem ? <Notice text={problem} /> : null}
      {preview ? <Image source={{ uri: preview }} style={styles.preview} resizeMode="contain" /> : null}
      <Button label="Take photo" onPress={() => run(takePhoto)} busy={busy} />
      <Button
        label="Choose from gallery"
        variant="secondary"
        onPress={() => run(pickFromGallery)}
        disabled={busy}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background, padding: spacing.lg, gap: spacing.md },
  intro: { fontSize: type.body, color: colors.text },
  preview: { flex: 1, borderRadius: 10 },
});

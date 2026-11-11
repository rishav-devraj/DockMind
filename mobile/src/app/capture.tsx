import { router, Stack } from 'expo-router';
import { useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import type { Fixture } from '@/api/types';
import { pickFromGallery, PickResult, takePhoto } from '@/capture/photo';
import { saveCapture } from '@/capture/saveCapture';
import { Screen } from '@/components/Screen';
import { Button, Notice } from '@/components/ui';
import { apiMode } from '@/config';
import { isOnline } from '@/lib/network';
import { colors, spacing, tapTarget, type } from '@/theme';

// X-Fixture picks the test note. It is only useful in development and is ignored by the real reader.
const FIXTURE_OPTIONS: { value: Fixture; label: string }[] = [
  { value: 'note-1', label: 'Clean' },
  { value: 'note-2', label: 'Short' },
  { value: 'note-3', label: 'Messy, slow' },
  { value: 'garbage', label: 'Unreadable' },
];
const showFixtures = __DEV__ || apiMode === 'mock';

export default function Capture() {
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [fixture, setFixture] = useState<Fixture>('note-2');
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
        const item = await saveCapture(result.asset, showFixtures ? fixture : null);
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
      {showFixtures ? (
        <View style={styles.fixtures}>
          <Text style={styles.fixtureTitle}>Test note</Text>
          <View style={styles.fixtureRow}>
            {FIXTURE_OPTIONS.map((option) => {
              const selected = option.value === fixture;
              return (
                <Pressable
                  key={option.value}
                  accessibilityRole="radio"
                  accessibilityState={{ selected }}
                  onPress={() => setFixture(option.value)}
                  style={[styles.fixture, selected && styles.fixtureSelected]}
                >
                  <Text style={[styles.fixtureLabel, selected && styles.fixtureLabelSelected]}>
                    {option.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>
      ) : null}
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
  fixtures: { gap: spacing.sm },
  fixtureTitle: { fontSize: type.small, fontWeight: '700', color: colors.textMuted },
  fixtureRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  fixture: {
    minHeight: tapTarget - 8,
    paddingHorizontal: spacing.md,
    justifyContent: 'center',
    borderRadius: 8,
    borderWidth: 2,
    borderColor: colors.primary,
    backgroundColor: colors.surface,
  },
  fixtureSelected: { backgroundColor: colors.primary },
  fixtureLabel: { fontSize: type.small, fontWeight: '700', color: colors.primary },
  fixtureLabelSelected: { color: colors.onPrimary },
});

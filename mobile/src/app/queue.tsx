import { router, Stack } from 'expo-router';
import { useMemo } from 'react';
import { FlatList, Image, StyleSheet, Text, View } from 'react-native';

import { Screen } from '@/components/Screen';
import { Button, Card, Message } from '@/components/ui';
import type { QueueItem, QueueStatus } from '@/queue/queue';
import { uploadQueue, useQueueItems } from '@/queue/runtime';
import { colors, spacing, type } from '@/theme';

const STATUS: Record<QueueStatus, { label: string; bg: string; text: string }> = {
  WAITING: { label: 'Waiting to sync', bg: colors.warningBg, text: colors.warningText },
  UPLOADING: { label: 'Uploading', bg: colors.primary, text: colors.onPrimary },
  SYNCED: { label: 'Synced', bg: '#067647', text: '#FFFFFF' },
  FAILED: { label: 'Failed', bg: colors.danger, text: colors.onDanger },
};

export default function Queue() {
  const items = useQueueItems();

  // Pending work first, in upload order. Finished items after it, newest first.
  const ordered = useMemo(() => {
    const pending = items.filter((item) => item.status !== 'SYNCED');
    const synced = items.filter((item) => item.status === 'SYNCED').reverse();
    return [...pending, ...synced];
  }, [items]);

  return (
    <Screen>
      <Stack.Screen options={{ title: 'Upload queue' }} />
      <FlatList
        data={ordered}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => <QueueRow item={item} />}
        contentContainerStyle={styles.list}
        ListEmptyComponent={
          <Message title="Nothing in the queue" detail="Captured notes will show up here." />
        }
      />
    </Screen>
  );
}

function QueueRow({ item }: { item: QueueItem }) {
  const status = STATUS[item.status];
  return (
    <Card>
      <View style={styles.row}>
        <Image source={{ uri: item.photoUri }} style={styles.thumb} />
        <View style={styles.details}>
          <Text style={styles.title}>Note {item.id}</Text>
          <Text style={styles.time}>{new Date(item.createdAt).toLocaleTimeString()}</Text>
          <View style={[styles.chip, { backgroundColor: status.bg }]}>
            <Text style={[styles.chipText, { color: status.text }]}>{status.label}</Text>
          </View>
        </View>
      </View>
      {item.lastError ? <Text style={styles.error}>{item.lastError}</Text> : null}
      {item.status === 'FAILED' ? (
        <Button label="Try again" onPress={() => void uploadQueue.retry(item.id)} />
      ) : null}
      {item.receiptId ? (
        <Button
          label="Open receipt"
          variant="secondary"
          onPress={() =>
            router.push({ pathname: '/review', params: { receiptId: item.receiptId ?? '' } })
          }
        />
      ) : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  list: { padding: spacing.lg, gap: spacing.md, flexGrow: 1 },
  row: { flexDirection: 'row', gap: spacing.md },
  thumb: { width: 72, height: 96, borderRadius: 6, backgroundColor: colors.disabledBg },
  details: { flex: 1, gap: spacing.xs, alignItems: 'flex-start' },
  title: { fontSize: type.title, fontWeight: '700', color: colors.text },
  time: { fontSize: type.small, color: colors.textMuted },
  chip: { borderRadius: 6, paddingHorizontal: spacing.sm, paddingVertical: spacing.xs },
  chipText: { fontSize: type.small, fontWeight: '700' },
  error: { fontSize: type.body, color: colors.danger, fontWeight: '600' },
});

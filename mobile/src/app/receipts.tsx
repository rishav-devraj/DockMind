import { router, Stack, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { FlatList, Pressable, RefreshControl, StyleSheet, Text } from 'react-native';

import { plainMessage } from '@/api/errors';
import type { Receipt } from '@/api/types';
import { Screen } from '@/components/Screen';
import { Loading, Message } from '@/components/ui';
import { api } from '@/lib/runtime';
import { STATUS_LABELS } from '@/review/labels';
import { colors, spacing, tapTarget, type } from '@/theme';

export default function Receipts() {
  const [receipts, setReceipts] = useState<Receipt[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      setReceipts(await api.listReceipts());
      setError(null);
    } catch (caught) {
      setError(plainMessage(caught));
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  const refresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  let body;
  if (error && !receipts) {
    body = <Message title="Could not load receipts" detail={error} actionLabel="Try again" onAction={load} />;
  } else if (!receipts) {
    body = <Loading message="Loading receipts" />;
  } else {
    body = (
      <FlatList
        data={receipts}
        keyExtractor={(receipt) => receipt.id}
        contentContainerStyle={styles.list}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
        ListEmptyComponent={<Message title="No receipts yet" detail="Capture a delivery note first." />}
        renderItem={({ item }) => (
          <Pressable
            accessibilityRole="button"
            style={styles.row}
            onPress={() => router.push({ pathname: '/review', params: { receiptId: item.id } })}
          >
            <Text style={styles.title}>{item.poNumber ?? 'No purchase order'}</Text>
            <Text style={styles.detail}>
              {item.id}, {STATUS_LABELS[item.status]}
            </Text>
          </Pressable>
        )}
      />
    );
  }

  return (
    <Screen>
      <Stack.Screen options={{ title: 'Receipts' }} />
      {body}
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { padding: spacing.lg, gap: spacing.md, flexGrow: 1 },
  row: {
    minHeight: tapTarget,
    padding: spacing.lg,
    gap: spacing.xs,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  title: { fontSize: type.title, fontWeight: '700', color: colors.text },
  detail: { fontSize: type.body, color: colors.textMuted },
});

import { router, Stack, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';

import { plainMessage } from '@/api/errors';
import type { Receipt, ReceiptStatus } from '@/api/types';
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
          <ReceiptCard
            receipt={item}
            onPress={() => router.push({ pathname: '/review', params: { receiptId: item.id } })}
          />
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

const STATUS_STYLE: Record<ReceiptStatus, { bg: string; text: string; accent: string }> = {
  CAPTURED: { bg: colors.warningBg, text: colors.warningText, accent: '#DC6803' },
  EXTRACTED: { bg: '#D1E9FF', text: colors.primary, accent: colors.primary },
  NEEDS_MANUAL: { bg: '#FEE4E2', text: colors.danger, accent: colors.danger },
  CONFIRMED: { bg: '#ECFDF3', text: '#067647', accent: '#12B76A' },
  DISPUTED: { bg: colors.danger, text: colors.onDanger, accent: colors.danger },
};

function ReceiptCard({ receipt, onPress }: { receipt: Receipt; onPress: () => void }) {
  const status = STATUS_STYLE[receipt.status];
  const lineCount = receipt.lines.length;
  const created = new Date(receipt.createdAt).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${receipt.poNumber ?? 'No purchase order'}, ${STATUS_LABELS[receipt.status]}`}
      onPress={onPress}
      style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}
    >
      <View style={[styles.accent, { backgroundColor: status.accent }]} />
      <View style={styles.body}>
        <View style={styles.headerRow}>
          <Text style={[styles.po, !receipt.poNumber && styles.poMissing]} numberOfLines={1}>
            {receipt.poNumber ?? 'No purchase order'}
          </Text>
          <View style={[styles.chip, { backgroundColor: status.bg }]}>
            <Text style={[styles.chipText, { color: status.text }]}>{STATUS_LABELS[receipt.status]}</Text>
          </View>
        </View>
        <Text style={styles.supplier} numberOfLines={1}>
          {receipt.supplier ?? 'Supplier not read yet'}
        </Text>
        <View style={styles.footer}>
          <Text style={styles.meta}>{receipt.id}</Text>
          <Text style={styles.meta}>
            {lineCount} {lineCount === 1 ? 'line' : 'lines'}
          </Text>
          <Text style={styles.meta}>{created}</Text>
        </View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  list: { padding: spacing.lg, gap: spacing.md, flexGrow: 1 },
  card: {
    minHeight: tapTarget,
    flexDirection: 'row',
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.disabledBg,
    shadowColor: '#101828',
    shadowOpacity: 0.06,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
    elevation: 2,
  },
  cardPressed: { opacity: 0.85 },
  accent: { width: 6 },
  body: { flex: 1, padding: spacing.lg, gap: spacing.xs },
  headerRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  po: { flex: 1, fontSize: type.title, fontWeight: '700', color: colors.text },
  poMissing: { color: colors.textMuted, fontWeight: '600' },
  chip: { borderRadius: 6, paddingHorizontal: spacing.sm, paddingVertical: spacing.xs },
  chipText: { fontSize: type.small, fontWeight: '700' },
  supplier: { fontSize: type.body, color: colors.textMuted },
  footer: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md, marginTop: spacing.xs },
  meta: { fontSize: type.small, color: colors.textMuted },
});

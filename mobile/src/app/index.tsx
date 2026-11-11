import { Stack } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { FlatList, RefreshControl, StyleSheet, Text, View } from 'react-native';

import { plainMessage } from '@/api/errors';
import type { PurchaseOrder } from '@/api/types';
import { Card, Loading, Message, Notice } from '@/components/ui';
import { loadOpenPurchaseOrders, PurchaseOrderList } from '@/lib/poCache';
import { colors, spacing, type } from '@/theme';

export default function OpenPurchaseOrders() {
  const [list, setList] = useState<PurchaseOrderList | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      setList(await loadOpenPurchaseOrders());
      setError(null);
    } catch (caught) {
      setError(plainMessage(caught));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  }, [load]);

  const retry = useCallback(() => {
    setError(null);
    void load();
  }, [load]);

  let body;
  if (list) {
    body = (
      <FlatList
        data={list.orders}
        keyExtractor={(order) => order.id}
        renderItem={({ item }) => <OrderCard order={item} />}
        contentContainerStyle={styles.list}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
        ListHeaderComponent={
          list.cachedAt ? (
            <Notice
              text={`Could not reach the server. Showing the list saved at ${new Date(
                list.cachedAt,
              ).toLocaleTimeString()}.`}
            />
          ) : undefined
        }
        ListEmptyComponent={
          <Message title="No open purchase orders" detail="Pull down to check again." />
        }
      />
    );
  } else if (error) {
    body = <Message title="Could not load purchase orders" detail={error} actionLabel="Try again" onAction={retry} />;
  } else {
    body = <Loading message="Loading purchase orders" />;
  }

  return (
    <View style={styles.screen}>
      <Stack.Screen options={{ title: 'Open POs' }} />
      {body}
    </View>
  );
}

function OrderCard({ order }: { order: PurchaseOrder }) {
  return (
    <Card>
      <Text style={styles.poNumber}>{order.poNumber}</Text>
      <Text style={styles.supplier}>{order.supplier}</Text>
      {order.lines.map((line) => (
        <View key={line.id} style={styles.line}>
          <Text style={styles.lineText}>{line.description}</Text>
          <Text style={styles.lineQty}>
            {line.orderedQty} {line.unit}
          </Text>
        </View>
      ))}
    </Card>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  list: { padding: spacing.lg, gap: spacing.md, flexGrow: 1 },
  poNumber: { fontSize: type.title, fontWeight: '700', color: colors.text },
  supplier: { fontSize: type.body, color: colors.textMuted },
  line: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing.md },
  lineText: { flex: 1, fontSize: type.body, color: colors.text },
  lineQty: { fontSize: type.body, fontWeight: '700', color: colors.text },
});

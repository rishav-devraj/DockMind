import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { plainMessage } from '../api/errors';
import type { PurchaseOrder, Receipt } from '../api/types';
import { Button, Card } from '../components/ui';
import { loadOpenPurchaseOrders } from '../lib/poCache';
import { api } from '../lib/runtime';
import { colors, spacing, type } from '../theme';
import { useAction } from './useAction';

interface PoPickerProps {
  receiptId: string;
  onAssigned: (receipt: Receipt) => void;
  onConflict: () => void;
}

export function PoPicker({ receiptId, onAssigned, onConflict }: PoPickerProps) {
  const [orders, setOrders] = useState<PurchaseOrder[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const { run, busy, error } = useAction(onConflict);

  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    loadOpenPurchaseOrders().then(
      (list) => {
        if (!active) return;
        setOrders(list.orders);
        setLoadError(null);
      },
      (caught) => {
        if (active) setLoadError(plainMessage(caught));
      },
    );
    return () => {
      active = false;
    };
  }, [attempt]);

  const assign = (poNumber: string) =>
    void run(async () => onAssigned(await api.assignPurchaseOrder(receiptId, poNumber)));

  return (
    <Card>
      <Text style={styles.title}>Which purchase order is this?</Text>
      <Text style={styles.hint}>Check the number and supplier on the paper note.</Text>
      {loadError ? <Text style={styles.error}>{loadError}</Text> : null}
      {loadError ? <Button label="Try again" onPress={() => setAttempt((value) => value + 1)} /> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <View style={styles.options}>
        {orders?.map((order) => (
          <Button
            key={order.id}
            label={`${order.poNumber}, ${order.supplier}`}
            variant="secondary"
            disabled={busy}
            onPress={() => assign(order.poNumber)}
          />
        ))}
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: type.title, fontWeight: '700', color: colors.text },
  hint: { fontSize: type.body, color: colors.textMuted },
  error: { fontSize: type.body, color: colors.danger, fontWeight: '600' },
  options: { gap: spacing.sm },
});

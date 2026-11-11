import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import type { Discrepancy, ReceiptLine } from '../api/types';
import { Card } from '../components/ui';
import { colors, discrepancyStyles, spacing, type } from '../theme';

export function DiscrepancyChip({ discrepancy }: { discrepancy: Discrepancy }) {
  const style = discrepancyStyles[discrepancy];
  return (
    <View style={[styles.chip, { backgroundColor: style.bg }]}>
      <Text style={[styles.chipText, { color: style.text }]}>{style.label}</Text>
    </View>
  );
}

const formatDelta = (delta: number) => (delta > 0 ? `+${delta}` : String(delta));

export function LineCard({ line, children }: { line: ReceiptLine; children?: ReactNode }) {
  return (
    <Card>
      <View style={styles.header}>
        <Text style={styles.description}>{line.description}</Text>
        <DiscrepancyChip discrepancy={line.discrepancy} />
      </View>

      <View style={styles.quantities}>
        <View style={styles.quantity}>
          <Text style={styles.caption}>Ordered</Text>
          <Text style={styles.value}>{line.orderedQty ?? 'None'}</Text>
        </View>
        <View style={styles.quantity}>
          <Text style={styles.caption}>Received</Text>
          <Text style={styles.value}>
            {line.receivedQty} {line.unit}
          </Text>
        </View>
        {line.delta !== null ? (
          <View style={styles.quantity}>
            <Text style={styles.caption}>Difference</Text>
            <Text style={styles.value}>{formatDelta(line.delta)}</Text>
          </View>
        ) : null}
      </View>

      <Text style={[styles.confidence, line.needsReview && styles.lowConfidence]}>
        {line.confidence === null
          ? 'Not on the note'
          : `AI confidence ${Math.round(line.confidence * 100)}%`}
        {line.needsReview ? ', check this line' : ''}
      </Text>
      {line.correctedByClerk ? (
        <Text style={styles.corrected}>
          Corrected by clerk{line.aiQty !== null ? ` (AI read ${line.aiQty})` : ''}
        </Text>
      ) : null}
      {children}
    </Card>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing.sm },
  description: { flex: 1, fontSize: type.title, fontWeight: '700', color: colors.text },
  chip: { borderRadius: 6, paddingHorizontal: spacing.sm, paddingVertical: spacing.xs },
  chipText: { fontSize: type.small, fontWeight: '700' },
  quantities: { flexDirection: 'row', gap: spacing.xl },
  quantity: { gap: 2 },
  caption: { fontSize: type.small, color: colors.textMuted },
  value: { fontSize: type.title, fontWeight: '700', color: colors.text },
  confidence: { fontSize: type.small, color: colors.textMuted },
  lowConfidence: { color: colors.danger, fontWeight: '700' },
  corrected: { fontSize: type.small, color: colors.primary, fontWeight: '700' },
});

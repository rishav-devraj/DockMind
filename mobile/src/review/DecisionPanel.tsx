import { useState } from 'react';
import { StyleSheet, Text, TextInput, View } from 'react-native';

import type { Receipt } from '../api/types';
import { Button, Card } from '../components/ui';
import { api } from '../lib/runtime';
import { colors, spacing, tapTarget, type } from '../theme';
import { confirmBlocker, ReviewChecks } from './decision';
import { useAction } from './useAction';

interface DecisionPanelProps {
  receipt: Receipt;
  checks: ReviewChecks;
  onDecided: (receipt: Receipt) => void;
  onConflict: () => void;
}

export function DecisionPanel({ receipt, checks, onDecided, onConflict }: DecisionPanelProps) {
  const [disputing, setDisputing] = useState(false);
  const [reason, setReason] = useState('');
  const { run, busy, error } = useAction(onConflict);
  const blocker = confirmBlocker(receipt, checks);

  const confirm = () => void run(async () => onDecided(await api.confirmReceipt(receipt.id)));
  const dispute = () =>
    void run(async () => onDecided(await api.disputeReceipt(receipt.id, reason.trim())));

  return (
    <Card>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {blocker ? <Text style={styles.blocker}>{blocker}</Text> : null}
      <Button label="Confirm delivery" onPress={confirm} disabled={!!blocker} busy={busy} />
      {disputing ? (
        <View style={styles.dispute}>
          <Text style={styles.label}>Why are you disputing this delivery?</Text>
          <TextInput
            accessibilityLabel="Dispute reason"
            style={styles.input}
            value={reason}
            onChangeText={setReason}
            multiline
            autoFocus
          />
          <Button
            label="Send dispute"
            variant="danger"
            onPress={dispute}
            disabled={reason.trim() === ''}
            busy={busy}
          />
          <Button
            label="Cancel"
            variant="secondary"
            onPress={() => setDisputing(false)}
            disabled={busy}
          />
        </View>
      ) : (
        <Button
          label="Dispute delivery"
          variant="secondary"
          onPress={() => setDisputing(true)}
          disabled={busy}
        />
      )}
    </Card>
  );
}

const styles = StyleSheet.create({
  error: { fontSize: type.body, color: colors.danger, fontWeight: '600' },
  blocker: { fontSize: type.body, color: colors.warningText, fontWeight: '600' },
  dispute: { gap: spacing.sm },
  label: { fontSize: type.body, fontWeight: '700', color: colors.text },
  input: {
    minHeight: tapTarget * 2,
    borderWidth: 2,
    borderColor: colors.border,
    borderRadius: 8,
    padding: spacing.md,
    fontSize: type.body,
    color: colors.text,
    textAlignVertical: 'top',
    backgroundColor: colors.surface,
  },
});

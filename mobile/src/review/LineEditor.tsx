import { useState } from 'react';
import { StyleSheet, Text, TextInput, View } from 'react-native';

import type { CorrectLineRequest, Receipt, ReceiptLine } from '../api/types';
import { Button } from '../components/ui';
import { api } from '../lib/runtime';
import { colors, spacing, tapTarget, type } from '../theme';
import { useAction } from './useAction';

interface LineEditorProps {
  receiptId: string;
  line: ReceiptLine;
  onSaved: (receipt: Receipt) => void;
  onConflict: () => void;
}

export function LineEditor({ receiptId, line, onSaved, onConflict }: LineEditorProps) {
  const [open, setOpen] = useState(false);
  const [quantity, setQuantity] = useState('');
  const [unit, setUnit] = useState('');
  const { run, busy, error, setError } = useAction(onConflict);

  const start = () => {
    setQuantity(String(line.receivedQty));
    setUnit(line.unit);
    setError(null);
    setOpen(true);
  };

  const save = () => {
    const text = quantity.trim().replace(',', '.');
    const receivedQty = Number(text);
    if (text === '' || !Number.isFinite(receivedQty) || receivedQty < 0) {
      setError('Enter a quantity of zero or more.');
      return;
    }
    const body: CorrectLineRequest = { receivedQty };
    const cleanUnit = unit.trim().toUpperCase();
    if (cleanUnit && cleanUnit !== line.unit) body.unit = cleanUnit;

    void run(async () => {
      onSaved(await api.correctLine(receiptId, line.id, body));
      setOpen(false);
    });
  };

  if (!open) return <Button label="Correct quantity" variant="secondary" onPress={start} />;

  return (
    <View style={styles.editor}>
      <View style={styles.inputs}>
        <View style={styles.quantityField}>
          <Text style={styles.label}>Received</Text>
          <TextInput
            accessibilityLabel={`Received quantity for ${line.description}`}
            style={styles.input}
            value={quantity}
            onChangeText={setQuantity}
            keyboardType="decimal-pad"
            selectTextOnFocus
            autoFocus
          />
        </View>
        <View style={styles.unitField}>
          <Text style={styles.label}>Unit</Text>
          <TextInput
            accessibilityLabel={`Unit for ${line.description}`}
            style={styles.input}
            value={unit}
            onChangeText={setUnit}
            autoCapitalize="characters"
            autoCorrect={false}
          />
        </View>
      </View>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <Button label="Save" onPress={save} busy={busy} />
      <Button label="Cancel" variant="secondary" onPress={() => setOpen(false)} disabled={busy} />
    </View>
  );
}

const styles = StyleSheet.create({
  editor: { gap: spacing.sm },
  inputs: { flexDirection: 'row', gap: spacing.md },
  quantityField: { flex: 2, gap: spacing.xs },
  unitField: { flex: 1, gap: spacing.xs },
  label: { fontSize: type.small, fontWeight: '700', color: colors.textMuted },
  input: {
    minHeight: tapTarget,
    borderWidth: 2,
    borderColor: colors.border,
    borderRadius: 8,
    paddingHorizontal: spacing.md,
    fontSize: type.title,
    color: colors.text,
    backgroundColor: colors.surface,
  },
  error: { fontSize: type.body, color: colors.danger, fontWeight: '600' },
});

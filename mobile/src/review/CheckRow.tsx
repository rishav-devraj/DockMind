import { Pressable, StyleSheet, Text, View } from 'react-native';

import { colors, spacing, tapTarget, type } from '../theme';

interface CheckRowProps {
  label: string;
  checked: boolean;
  onToggle: () => void;
}

export function CheckRow({ label, checked, onToggle }: CheckRowProps) {
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityState={{ checked }}
      onPress={onToggle}
      style={styles.row}
    >
      <View style={[styles.box, checked && styles.boxChecked]}>
        {checked ? <Text style={styles.tick}>✓</Text> : null}
      </View>
      <Text style={styles.label}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { minHeight: tapTarget, flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  box: {
    width: 36,
    height: 36,
    borderRadius: 6,
    borderWidth: 3,
    borderColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surface,
  },
  boxChecked: { backgroundColor: colors.primary },
  tick: { color: colors.onPrimary, fontSize: type.title, fontWeight: '700' },
  label: { flex: 1, fontSize: type.body, fontWeight: '600', color: colors.text },
});

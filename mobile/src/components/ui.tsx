import type { ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import { colors, spacing, tapTarget, type } from '../theme';

type ButtonVariant = 'primary' | 'secondary' | 'danger';

interface ButtonProps {
  label: string;
  onPress: () => void;
  variant?: ButtonVariant;
  disabled?: boolean;
  busy?: boolean;
}

export function Button({ label, onPress, variant = 'primary', disabled, busy }: ButtonProps) {
  const inactive = disabled || busy;
  const palette = inactive ? inactiveStyle : variantStyles[variant];
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!inactive, busy: !!busy }}
      disabled={inactive}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: palette.bg, borderColor: palette.border },
        pressed && styles.pressed,
      ]}
    >
      {busy ? <ActivityIndicator color={palette.text} /> : null}
      <Text style={[styles.buttonLabel, { color: palette.text }]}>{label}</Text>
    </Pressable>
  );
}

const variantStyles: Record<ButtonVariant, { bg: string; text: string; border: string }> = {
  primary: { bg: colors.primary, text: colors.onPrimary, border: colors.primary },
  secondary: { bg: colors.surface, text: colors.primary, border: colors.primary },
  danger: { bg: colors.danger, text: colors.onDanger, border: colors.danger },
};

const inactiveStyle = {
  bg: colors.disabledBg,
  text: colors.disabledText,
  border: colors.disabledBg,
};

export function Card({ children }: { children: ReactNode }) {
  return <View style={styles.card}>{children}</View>;
}

export function Loading({ message }: { message: string }) {
  return (
    <View style={styles.centered}>
      <ActivityIndicator size="large" color={colors.primary} />
      <Text style={styles.centeredText}>{message}</Text>
    </View>
  );
}

interface MessageProps {
  title: string;
  detail?: string;
  actionLabel?: string;
  onAction?: () => void;
}

export function Message({ title, detail, actionLabel, onAction }: MessageProps) {
  return (
    <View style={styles.centered}>
      <Text style={styles.messageTitle}>{title}</Text>
      {detail ? <Text style={styles.centeredText}>{detail}</Text> : null}
      {actionLabel && onAction ? (
        <View style={styles.messageAction}>
          <Button label={actionLabel} onPress={onAction} />
        </View>
      ) : null}
    </View>
  );
}

export function Notice({ text }: { text: string }) {
  return (
    <View style={styles.notice}>
      <Text style={styles.noticeText}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: tapTarget,
    borderRadius: 10,
    borderWidth: 2,
    paddingHorizontal: spacing.lg,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.sm,
  },
  pressed: { opacity: 0.75 },
  buttonLabel: { fontSize: type.body, fontWeight: '700' },
  card: {
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: spacing.lg,
    gap: spacing.sm,
    borderWidth: 1,
    borderColor: colors.border,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
    gap: spacing.md,
  },
  centeredText: { fontSize: type.body, color: colors.textMuted, textAlign: 'center' },
  messageTitle: { fontSize: type.title, fontWeight: '700', color: colors.text, textAlign: 'center' },
  messageAction: { alignSelf: 'stretch', marginTop: spacing.sm },
  notice: { backgroundColor: colors.warningBg, borderRadius: 10, padding: spacing.md },
  noticeText: { fontSize: type.body, color: colors.warningText, fontWeight: '600' },
});

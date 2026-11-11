import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useIsOnline } from '../lib/network';
import { colors, spacing, type } from '../theme';

/** Every screen sits inside this, so the offline banner shows across the whole app. */
export function Screen({ children }: { children: ReactNode }) {
  const online = useIsOnline();
  const { bottom } = useSafeAreaInsets();
  return (
    <View style={[styles.screen, { paddingBottom: bottom }]}>
      {online ? null : (
        <View style={styles.banner} accessibilityRole="alert">
          <Text style={styles.bannerText}>No signal. Photos are saved and upload later.</Text>
        </View>
      )}
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  banner: { backgroundColor: colors.offlineBg, padding: spacing.md },
  bannerText: {
    color: colors.offlineText,
    fontSize: type.body,
    fontWeight: '700',
    textAlign: 'center',
  },
});

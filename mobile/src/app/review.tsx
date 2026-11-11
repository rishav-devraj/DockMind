import { useHeaderHeight } from '@react-navigation/elements';
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { Image, KeyboardAvoidingView, ScrollView, StyleSheet, Text, View } from 'react-native';

import type { Receipt } from '@/api/types';
import { Screen } from '@/components/Screen';
import { Button, Card, Loading, Message, Notice } from '@/components/ui';
import { api } from '@/lib/runtime';
import { uploadQueue, useQueueItems } from '@/queue/runtime';
import { CheckRow } from '@/review/CheckRow';
import { needsPoCheck } from '@/review/decision';
import { DecisionPanel } from '@/review/DecisionPanel';
import { STATUS_LABELS } from '@/review/labels';
import { LineCard } from '@/review/LineCard';
import { LineEditor } from '@/review/LineEditor';
import { PoPicker } from '@/review/PoPicker';
import { useReceipt } from '@/review/useReceipt';
import { colors, spacing, type } from '@/theme';

export default function Review() {
  const params = useLocalSearchParams<{ itemId?: string; receiptId?: string }>();
  const item = useQueueItems().find((candidate) => String(candidate.id) === params.itemId);
  const receiptId = params.receiptId ?? item?.receiptId ?? null;
  const { receipt, setReceipt, error, slow, retry } = useReceipt(receiptId);
  const headerHeight = useHeaderHeight();

  let body;
  if (!receiptId && item?.status === 'FAILED') {
    body = (
      <Message
        title="This photo could not be uploaded"
        detail={item.lastError ?? undefined}
        actionLabel="Try again"
        onAction={() => void uploadQueue.retry(item.id)}
      />
    );
  } else if (!receiptId && item?.status === 'WAITING') {
    body = (
      <Message
        title="Waiting to upload"
        detail={item.lastError ?? 'The photo is saved. It will upload by itself.'}
        actionLabel="See upload queue"
        onAction={() => router.replace('/queue')}
      />
    );
  } else if (!receiptId && !params.itemId) {
    body = <Message title="No receipt selected" />;
  } else if (!receiptId) {
    body = <Loading message="Uploading the photo" />;
  } else if (!receipt && error) {
    body = (
      <Message title="Could not open this receipt" detail={error} actionLabel="Try again" onAction={retry} />
    );
  } else if (!receipt) {
    body = <Loading message="Opening the receipt" />;
  } else if (receipt.status === 'CAPTURED') {
    body = (
      <Loading
        message={
          error ?? (slow ? 'Still reading the note, this can take 30 seconds' : 'Reading the note')
        }
      />
    );
  } else {
    body = <ReceiptReview receipt={receipt} onChange={setReceipt} onConflict={retry} />;
  }

  return (
    <Screen>
      <Stack.Screen options={{ title: receipt?.id ?? 'Review' }} />
      <KeyboardAvoidingView style={styles.fill} behavior="padding" keyboardVerticalOffset={headerHeight}>
        {body}
      </KeyboardAvoidingView>
    </Screen>
  );
}

interface ReceiptReviewProps {
  receipt: Receipt;
  onChange: (next: Receipt) => void;
  onConflict: () => void;
}

function ReceiptReview({ receipt, onChange, onConflict }: ReceiptReviewProps) {
  const [showPhoto, setShowPhoto] = useState(false);
  const photoUri = api.receiptImageUri(receipt.id);
  const editable = receipt.status === 'EXTRACTED' || receipt.status === 'NEEDS_MANUAL';
  const [checkedLines, setCheckedLines] = useState<ReadonlySet<string>>(new Set());
  const [poChecked, setPoChecked] = useState(false);
  const checks = useMemo(() => ({ lineIds: checkedLines, poChecked }), [checkedLines, poChecked]);

  const toggleLine = (lineId: string) =>
    setCheckedLines((current) => {
      const next = new Set(current);
      if (!next.delete(lineId)) next.add(lineId);
      return next;
    });

  return (
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Card>
        <Text style={styles.status}>{STATUS_LABELS[receipt.status]}</Text>
        <Text style={styles.po}>{receipt.poNumber ?? 'No purchase order matched'}</Text>
        {receipt.supplier ? <Text style={styles.detail}>{receipt.supplier}</Text> : null}
        {receipt.deliveryNoteNo ? (
          <Text style={styles.detail}>Delivery note {receipt.deliveryNoteNo}</Text>
        ) : null}
        {receipt.overallConfidence !== null ? (
          <Text style={styles.detail}>
            AI confidence {Math.round(receipt.overallConfidence * 100)}%
          </Text>
        ) : null}
      </Card>

      {editable && needsPoCheck(receipt) ? (
        <Card>
          <CheckRow
            label={`${receipt.poNumber} and ${receipt.supplier ?? 'the supplier'} match the paper note`}
            checked={poChecked}
            onToggle={() => setPoChecked((value) => !value)}
          />
        </Card>
      ) : null}
      {receipt.status === 'NEEDS_MANUAL' ? (
        <Notice text="The note could not be read. Pick the purchase order, then enter the quantities by hand." />
      ) : null}
      {editable && receipt.poNumber === null ? (
        <PoPicker receiptId={receipt.id} onAssigned={onChange} onConflict={onConflict} />
      ) : null}
      {receipt.aiNote ? (
        <Card>
          <Text style={styles.caption}>AI summary</Text>
          <Text style={styles.note}>{receipt.aiNote}</Text>
        </Card>
      ) : null}
      {receipt.freeText ? (
        <Card>
          <Text style={styles.caption}>Also written on the note</Text>
          <Text style={styles.note}>{receipt.freeText}</Text>
        </Card>
      ) : null}
      {receipt.disputeReason ? (
        <Card>
          <Text style={styles.caption}>Dispute reason</Text>
          <Text style={styles.note}>{receipt.disputeReason}</Text>
        </Card>
      ) : null}

      {receipt.lines.map((line) => (
        <LineCard key={line.id} line={line}>
          {editable ? (
            <LineEditor
              receiptId={receipt.id}
              line={line}
              onSaved={onChange}
              onConflict={onConflict}
            />
          ) : null}
          {editable && line.needsReview ? (
            <CheckRow
              label="I checked this line against the paper"
              checked={checkedLines.has(line.id)}
              onToggle={() => toggleLine(line.id)}
            />
          ) : null}
        </LineCard>
      ))}

      {photoUri ? (
        <View style={styles.photo}>
          <Button
            label={showPhoto ? 'Hide photo' : 'Show photo of the note'}
            variant="secondary"
            onPress={() => setShowPhoto((value) => !value)}
          />
          {showPhoto ? (
            <Image source={{ uri: photoUri }} style={styles.image} resizeMode="contain" />
          ) : null}
        </View>
      ) : null}

      {editable ? (
        <DecisionPanel
          receipt={receipt}
          checks={checks}
          onDecided={onChange}
          onConflict={onConflict}
        />
      ) : (
        <Button label="Back to purchase orders" onPress={() => router.dismissAll()} />
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  content: { padding: spacing.lg, gap: spacing.md },
  status: { fontSize: type.small, fontWeight: '700', color: colors.primary },
  po: { fontSize: type.heading, fontWeight: '700', color: colors.text },
  detail: { fontSize: type.body, color: colors.textMuted },
  caption: { fontSize: type.small, fontWeight: '700', color: colors.textMuted },
  note: { fontSize: type.body, color: colors.text },
  photo: { gap: spacing.sm },
  image: { width: '100%', height: 420, borderRadius: 10, backgroundColor: colors.disabledBg },
});

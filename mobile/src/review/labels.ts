import type { ReceiptStatus } from '../api/types';

export const STATUS_LABELS: Record<ReceiptStatus, string> = {
  CAPTURED: 'Reading the note',
  EXTRACTED: 'Ready to review',
  NEEDS_MANUAL: 'Needs manual entry',
  CONFIRMED: 'Confirmed',
  DISPUTED: 'Disputed',
};

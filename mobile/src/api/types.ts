// Mirrors CONTRACT.md. Every server-shaped type lives in this one file.

export type ReceiptStatus = 'CAPTURED' | 'EXTRACTED' | 'NEEDS_MANUAL' | 'CONFIRMED' | 'DISPUTED';

export type Discrepancy =
  | 'MATCH'
  | 'SHORT'
  | 'OVER'
  | 'UNIT_MISMATCH'
  | 'UNKNOWN_ITEM'
  | 'NOT_DELIVERED';

export type Fixture = 'note-1' | 'note-2' | 'note-3' | 'garbage';

export interface PurchaseOrderLine {
  id: string;
  sku: string;
  description: string;
  orderedQty: number;
  unit: string;
}

export interface PurchaseOrder {
  id: string;
  poNumber: string;
  supplier: string;
  status: string;
  lines: PurchaseOrderLine[];
}

export interface ReceiptLine {
  id: string;
  poLineId: string | null;
  description: string;
  orderedQty: number | null;
  receivedQty: number;
  unit: string;
  confidence: number | null;
  discrepancy: Discrepancy;
  delta: number | null;
  aiQty: number | null;
  correctedByClerk: boolean;
  needsReview: boolean;
}

export interface Receipt {
  id: string;
  status: ReceiptStatus;
  poNumber: string | null;
  supplier: string | null;
  overallConfidence: number | null;
  aiNote: string | null;
  lines: ReceiptLine[];
  deliveryNoteNo: string | null;
  poConfidence: number | null;
  freeText: string | null;
  needsReview: boolean;
  disputeReason: string | null;
  clerk: string;
  createdAt: string;
  updatedAt: string;
}

export interface ApiErrorBody {
  error: string;
  message: string;
}

export interface UploadRequest {
  imageUri: string;
  idempotencyKey: string;
  fixture?: Fixture | null;
}

export interface UploadResult {
  receipt: Receipt;
  /** True when the server already knew this key (200 instead of 202). */
  replayed: boolean;
}

export interface CorrectLineRequest {
  receivedQty: number;
  unit?: string;
}

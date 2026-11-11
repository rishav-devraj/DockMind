import type { Fixture, PurchaseOrder } from './types';

// Seed data from the brief's Addendum B.
export const SEED_PURCHASE_ORDERS: PurchaseOrder[] = [
  {
    id: 'PO-4501',
    poNumber: 'PO-4501',
    supplier: 'Fresh Farm Produce Ltd',
    status: 'OPEN',
    lines: [
      { id: 'POL-4501-1', sku: 'SKU-APL-01', description: 'Gala Apples', orderedQty: 200, unit: 'KG' },
      { id: 'POL-4501-2', sku: 'SKU-ORG-02', description: 'Oranges, Navel', orderedQty: 150, unit: 'KG' },
      { id: 'POL-4501-3', sku: 'SKU-LET-03', description: 'Lettuce, Romaine', orderedQty: 60, unit: 'CASE' },
    ],
  },
  {
    id: 'PO-4502',
    poNumber: 'PO-4502',
    supplier: 'Blue Ocean Seafood Co.',
    status: 'OPEN',
    lines: [
      { id: 'POL-4502-1', sku: 'SKU-SAL-10', description: 'Salmon fillet', orderedQty: 80, unit: 'KG' },
      { id: 'POL-4502-2', sku: 'SKU-PRW-11', description: 'Tiger Prawns', orderedQty: 40, unit: 'KG' },
    ],
  },
  {
    id: 'PO-4503',
    poNumber: 'PO-4503',
    supplier: 'Dairy Direct',
    status: 'OPEN',
    lines: [
      { id: 'POL-4503-1', sku: 'SKU-MLK-20', description: 'Milk 1L', orderedQty: 240, unit: 'EA' },
      { id: 'POL-4503-2', sku: 'SKU-BTR-21', description: 'Butter 500g', orderedQty: 100, unit: 'EA' },
      { id: 'POL-4503-3', sku: 'SKU-CHS-22', description: 'Cheddar, mature', orderedQty: 40, unit: 'KG' },
    ],
  },
  {
    id: 'PO-4513',
    poNumber: 'PO-4513',
    supplier: 'Green Valley Eggs',
    status: 'OPEN',
    lines: [
      { id: 'POL-4513-1', sku: 'SKU-EGG-30', description: 'Eggs, large, tray', orderedQty: 120, unit: 'TRAY' },
    ],
  },
];

export interface FixtureLine {
  description: string;
  poLineId: string | null;
  qty: number;
  unit: string;
  confidence: number;
}

export interface FixtureNote {
  /** How long the mock pretends the vision call takes. */
  delayMs: number;
  /** Null means the read failed and the receipt ends in NEEDS_MANUAL. */
  extraction: {
    supplier: string;
    deliveryNoteNo: string;
    poNumber: string;
    poConfidence: number;
    overallConfidence: number;
    aiNote: string;
    freeText: string | null;
    lines: FixtureLine[];
  } | null;
}

export const FIXTURES: Record<Fixture, FixtureNote> = {
  // Clean note, everything matches.
  'note-1': {
    delayMs: 2_500,
    extraction: {
      supplier: 'Fresh Farm Produce Ltd',
      deliveryNoteNo: 'DN-30412',
      poNumber: 'PO-4501',
      poConfidence: 0.99,
      overallConfidence: 0.98,
      aiNote: 'Everything on this note matches the purchase order.',
      freeText: null,
      lines: [
        { description: 'Gala Apples', poLineId: 'POL-4501-1', qty: 200, unit: 'KGS', confidence: 0.99 },
        { description: 'Oranges, Navel', poLineId: 'POL-4501-2', qty: 150, unit: 'kg', confidence: 0.98 },
        { description: 'Lettuce, Romaine', poLineId: 'POL-4501-3', qty: 60, unit: 'CASES', confidence: 0.97 },
      ],
    },
  },
  // The contract's example: a shortage and an item nobody ordered.
  'note-2': {
    delayMs: 4_000,
    extraction: {
      supplier: 'Blue Ocean Seafood Co.',
      deliveryNoteNo: 'DN-30417',
      poNumber: 'PO-4502',
      poConfidence: 0.97,
      overallConfidence: 0.94,
      aiNote: "Salmon is 8 kg short and scallops weren't ordered.",
      freeText: null,
      lines: [
        { description: 'Salmon fillet', poLineId: 'POL-4502-1', qty: 72, unit: 'KG', confidence: 0.95 },
        { description: 'Tiger Prawns', poLineId: 'POL-4502-2', qty: 40, unit: 'KG', confidence: 0.96 },
        { description: 'Scallops, dived', poLineId: null, qty: 10, unit: 'KG', confidence: 0.93 },
      ],
    },
  },
  // Handwritten note: slow call, low confidence, unit mismatch, one line missing.
  'note-3': {
    delayMs: 15_000,
    extraction: {
      supplier: 'Dairy Direct',
      deliveryNoteNo: 'DN-30422',
      poNumber: 'PO-4503',
      poConfidence: 0.71,
      overallConfidence: 0.63,
      aiNote:
        'The handwriting is hard to read. Milk may be 26 short, butter was counted in cases, and no cheddar is listed.',
      freeText: '2 crates dented',
      lines: [
        { description: 'Milk 1L', poLineId: 'POL-4503-1', qty: 214, unit: 'EACH', confidence: 0.62 },
        { description: 'Butter 500g', poLineId: 'POL-4503-2', qty: 4, unit: 'CASES', confidence: 0.58 },
      ],
    },
  },
  // Model failure: nothing readable came back.
  garbage: { delayMs: 3_000, extraction: null },
};

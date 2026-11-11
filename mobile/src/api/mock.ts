import type { ApiClient } from './client';
import { ApiError, NetworkError } from './errors';
import { FIXTURES, SEED_PURCHASE_ORDERS } from './mockData';
import type {
  CorrectLineRequest,
  Fixture,
  PurchaseOrder,
  PurchaseOrderLine,
  Receipt,
  ReceiptLine,
  ReceiptStatus,
  UploadRequest,
  UploadResult,
} from './types';

const REVIEW_THRESHOLD = 0.8;
const MAX_IMAGE_BYTES = 10 * 1024 * 1024;

const UNIT_ALIASES: Record<string, string> = {
  KGS: 'KG',
  CASES: 'CASE',
  EACH: 'EA',
  PCS: 'EA',
  TRAYS: 'TRAY',
};

export interface MockPersistence {
  load(): Promise<string | null>;
  save(state: string): Promise<void>;
}

export interface MockOptions {
  isOnline?: () => boolean;
  persistence?: MockPersistence;
  now?: () => number;
  latencyMs?: number;
  imageSize?: (uri: string) => number;
}

interface MockRecord {
  receipt: Receipt;
  fixture: Fixture;
  extractAt: number;
  imageUri: string;
}

interface MockState {
  nextId: number;
  idsByKey: Record<string, string>;
  records: Record<string, MockRecord>;
}

const normalizeUnit = (unit: string) => {
  const upper = unit.trim().toUpperCase();
  return UNIT_ALIASES[upper] ?? upper;
};

const normalizeDescription = (text: string) => text.trim().toLowerCase().replace(/\s+/g, ' ');

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

const findPoLine = (poLineId: string | null): PurchaseOrderLine | undefined =>
  SEED_PURCHASE_ORDERS.flatMap((po) => po.lines).find((line) => line.id === poLineId);

/** Contract matching rules, in contract order. The real backend owns this logic. */
function applyMatching(line: ReceiptLine): void {
  const poLine = findPoLine(line.poLineId);
  line.needsReview =
    line.confidence !== null && line.confidence < REVIEW_THRESHOLD && !line.correctedByClerk;

  if (!poLine) {
    line.poLineId = null;
    line.orderedQty = null;
    line.delta = null;
    line.discrepancy = 'UNKNOWN_ITEM';
    return;
  }

  line.orderedQty = poLine.orderedQty;
  if (normalizeUnit(line.unit) !== normalizeUnit(poLine.unit)) {
    line.delta = null;
    line.discrepancy = 'UNIT_MISMATCH';
    return;
  }

  line.delta = line.receivedQty - poLine.orderedQty;
  if (line.receivedQty === 0) line.discrepancy = 'NOT_DELIVERED';
  else if (line.delta < 0) line.discrepancy = 'SHORT';
  else if (line.delta > 0) line.discrepancy = 'OVER';
  else line.discrepancy = 'MATCH';
}

function notDeliveredLine(id: string, poLine: PurchaseOrderLine): ReceiptLine {
  const line: ReceiptLine = {
    id,
    poLineId: poLine.id,
    description: poLine.description,
    orderedQty: poLine.orderedQty,
    receivedQty: 0,
    unit: poLine.unit,
    confidence: null,
    discrepancy: 'NOT_DELIVERED',
    delta: -poLine.orderedQty,
    aiQty: null,
    correctedByClerk: false,
    needsReview: false,
  };
  applyMatching(line);
  return line;
}

function refreshReviewFlag(receipt: Receipt): void {
  receipt.needsReview =
    receipt.poNumber === null ||
    (receipt.poConfidence !== null && receipt.poConfidence < REVIEW_THRESHOLD) ||
    receipt.lines.some((line) => line.needsReview);
}

const isTerminal = (status: ReceiptStatus) => status === 'CONFIRMED' || status === 'DISPUTED';

export class MockApiClient implements ApiClient {
  private readonly isOnline: () => boolean;
  private readonly persistence?: MockPersistence;
  private readonly now: () => number;
  private readonly latencyMs: number;
  private readonly imageSize: (uri: string) => number;
  private state: MockState = { nextId: 1042, idsByKey: {}, records: {} };
  private loaded?: Promise<void>;

  constructor(options: MockOptions = {}) {
    this.isOnline = options.isOnline ?? (() => true);
    this.persistence = options.persistence;
    this.now = options.now ?? Date.now;
    this.latencyMs = options.latencyMs ?? 350;
    this.imageSize = options.imageSize ?? (() => 0);
  }

  async listOpenPurchaseOrders(): Promise<PurchaseOrder[]> {
    await this.enter();
    return clone(SEED_PURCHASE_ORDERS);
  }

  async listReceipts(status?: ReceiptStatus): Promise<Receipt[]> {
    await this.enter();
    const records = Object.values(this.state.records);
    records.forEach((record) => this.advance(record));
    await this.persist();
    return clone(
      records
        .map((record) => record.receipt)
        .filter((receipt) => !status || receipt.status === status)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id)),
    );
  }

  async getReceipt(id: string): Promise<Receipt> {
    await this.enter();
    const record = this.record(id);
    this.advance(record);
    await this.persist();
    return clone(record.receipt);
  }

  async uploadReceipt({ imageUri, idempotencyKey, fixture }: UploadRequest): Promise<UploadResult> {
    await this.enter();
    if (!idempotencyKey) throw new ApiError(400, 'MISSING_KEY', 'Idempotency-Key is required.');
    if (!imageUri) throw new ApiError(400, 'MISSING_IMAGE', 'The photo is missing.');
    if (this.imageSize(imageUri) > MAX_IMAGE_BYTES) {
      throw new ApiError(413, 'IMAGE_TOO_LARGE', 'The photo is larger than 10 MB.');
    }

    const existingId = this.state.idsByKey[idempotencyKey];
    if (existingId) {
      const existing = this.record(existingId);
      this.advance(existing);
      await this.persist();
      return { receipt: clone(existing.receipt), replayed: true };
    }

    const chosen = fixture ?? 'note-2';
    const id = `RCP-${this.state.nextId++}`;
    const timestamp = new Date(this.now()).toISOString();
    const receipt: Receipt = {
      id,
      status: 'CAPTURED',
      poNumber: null,
      supplier: null,
      overallConfidence: null,
      aiNote: null,
      lines: [],
      deliveryNoteNo: null,
      poConfidence: null,
      freeText: null,
      needsReview: false,
      disputeReason: null,
      clerk: 'clerk-01',
      createdAt: timestamp,
      updatedAt: timestamp,
    };
    this.state.idsByKey[idempotencyKey] = id;
    this.state.records[id] = {
      receipt,
      fixture: chosen,
      extractAt: this.now() + FIXTURES[chosen].delayMs,
      imageUri,
    };
    await this.persist();
    return { receipt: clone(receipt), replayed: false };
  }

  async correctLine(
    receiptId: string,
    lineId: string,
    { receivedQty, unit }: CorrectLineRequest,
  ): Promise<Receipt> {
    await this.enter();
    const record = this.record(receiptId);
    this.advance(record);
    const { receipt } = record;
    const line = receipt.lines.find((candidate) => candidate.id === lineId);
    if (!line) throw new ApiError(404, 'NOT_FOUND', 'That line is not on this receipt.');
    if (isTerminal(receipt.status)) {
      throw new ApiError(409, 'TERMINAL', 'This receipt is already decided and cannot be edited.');
    }
    if (typeof receivedQty !== 'number' || !Number.isFinite(receivedQty) || receivedQty < 0) {
      throw new ApiError(400, 'INVALID_BODY', 'Enter a quantity of zero or more.');
    }

    line.receivedQty = receivedQty;
    if (unit) line.unit = normalizeUnit(unit);
    line.correctedByClerk = true;
    applyMatching(line);
    return this.commit(receipt);
  }

  async confirmReceipt(id: string): Promise<Receipt> {
    await this.enter();
    const record = this.record(id);
    this.advance(record);
    const { receipt } = record;
    if (receipt.status === 'CONFIRMED') return clone(receipt);
    if (receipt.status === 'DISPUTED') {
      throw new ApiError(409, 'ALREADY_DISPUTED', 'This receipt was already disputed.');
    }
    if (receipt.status === 'CAPTURED') {
      throw new ApiError(409, 'NOT_READY', 'The note is still being read.');
    }
    if (receipt.poNumber === null) {
      throw new ApiError(409, 'NO_PO', 'Pick the purchase order before confirming.');
    }
    receipt.status = 'CONFIRMED';
    return this.commit(receipt);
  }

  async disputeReceipt(id: string, reason: string): Promise<Receipt> {
    await this.enter();
    const record = this.record(id);
    this.advance(record);
    const { receipt } = record;
    if (!reason || !reason.trim()) {
      throw new ApiError(400, 'BLANK_REASON', 'Give a reason for the dispute.');
    }
    if (receipt.status === 'DISPUTED') return clone(receipt);
    if (receipt.status === 'CONFIRMED') {
      throw new ApiError(409, 'ALREADY_CONFIRMED', 'This receipt was already confirmed.');
    }
    if (receipt.status === 'CAPTURED') {
      throw new ApiError(409, 'NOT_READY', 'The note is still being read.');
    }
    receipt.status = 'DISPUTED';
    receipt.disputeReason = reason.trim();
    return this.commit(receipt);
  }

  async assignPurchaseOrder(id: string, poNumber: string): Promise<Receipt> {
    await this.enter();
    const record = this.record(id);
    this.advance(record);
    const { receipt } = record;
    const po = SEED_PURCHASE_ORDERS.find((candidate) => candidate.poNumber === poNumber);
    if (!po) throw new ApiError(404, 'NOT_FOUND', `${poNumber} does not exist.`);
    if (isTerminal(receipt.status)) {
      throw new ApiError(409, 'TERMINAL', 'This receipt is already decided and cannot be edited.');
    }
    if (receipt.status === 'CAPTURED') {
      throw new ApiError(409, 'NOT_READY', 'The note is still being read.');
    }

    const linked = new Set<string>();
    for (const line of receipt.lines) {
      const match = po.lines.find(
        (poLine) => normalizeDescription(poLine.description) === normalizeDescription(line.description),
      );
      line.poLineId = match?.id ?? null;
      if (match) linked.add(match.id);
      applyMatching(line);
    }
    let nextLine = receipt.lines.length + 1;
    for (const poLine of po.lines) {
      if (!linked.has(poLine.id)) receipt.lines.push(notDeliveredLine(`RL-${nextLine++}`, poLine));
    }

    receipt.poNumber = po.poNumber;
    receipt.supplier = po.supplier;
    receipt.poConfidence = 1;
    receipt.status = 'EXTRACTED';
    return this.commit(receipt);
  }

  receiptImageUri(id: string): string | null {
    return this.state.records[id]?.imageUri ?? null;
  }

  private async enter(): Promise<void> {
    this.loaded ??= this.load();
    await this.loaded;
    if (this.latencyMs > 0) await new Promise((resolve) => setTimeout(resolve, this.latencyMs));
    if (!this.isOnline()) throw new NetworkError('Network request failed');
  }

  private async load(): Promise<void> {
    const saved = await this.persistence?.load();
    if (saved) this.state = JSON.parse(saved) as MockState;
  }

  private async persist(): Promise<void> {
    await this.persistence?.save(JSON.stringify(this.state));
  }

  private async commit(receipt: Receipt): Promise<Receipt> {
    refreshReviewFlag(receipt);
    receipt.updatedAt = new Date(this.now()).toISOString();
    await this.persist();
    return clone(receipt);
  }

  private record(id: string): MockRecord {
    const record = this.state.records[id];
    if (!record) throw new ApiError(404, 'NOT_FOUND', 'That receipt does not exist.');
    return record;
  }

  /** Moves a receipt out of CAPTURED once its pretend vision call has finished. */
  private advance(record: MockRecord): void {
    const { receipt } = record;
    if (receipt.status !== 'CAPTURED' || this.now() < record.extractAt) return;

    const { extraction } = FIXTURES[record.fixture];
    receipt.updatedAt = new Date(this.now()).toISOString();
    if (!extraction) {
      receipt.status = 'NEEDS_MANUAL';
      refreshReviewFlag(receipt);
      return;
    }

    const po = SEED_PURCHASE_ORDERS.find((candidate) => candidate.poNumber === extraction.poNumber);
    receipt.lines = extraction.lines.map((source, index) => {
      const line: ReceiptLine = {
        id: `RL-${index + 1}`,
        poLineId: source.poLineId,
        description: source.description,
        orderedQty: null,
        receivedQty: source.qty,
        unit: normalizeUnit(source.unit),
        confidence: source.confidence,
        discrepancy: 'UNKNOWN_ITEM',
        delta: null,
        aiQty: source.qty,
        correctedByClerk: false,
        needsReview: false,
      };
      applyMatching(line);
      return line;
    });

    const linked = new Set(receipt.lines.map((line) => line.poLineId));
    for (const poLine of po?.lines ?? []) {
      if (!linked.has(poLine.id)) {
        receipt.lines.push(notDeliveredLine(`RL-${receipt.lines.length + 1}`, poLine));
      }
    }

    receipt.status = 'EXTRACTED';
    receipt.poNumber = extraction.poNumber;
    receipt.supplier = extraction.supplier;
    receipt.overallConfidence = extraction.overallConfidence;
    receipt.aiNote = extraction.aiNote;
    receipt.deliveryNoteNo = extraction.deliveryNoteNo;
    receipt.poConfidence = extraction.poConfidence;
    receipt.freeText = extraction.freeText;
    refreshReviewFlag(receipt);
  }
}

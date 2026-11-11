import { ApiError, NetworkError } from './errors';
import { MockApiClient } from './mock';

function setup(options: { online?: boolean; imageBytes?: number } = {}) {
  let time = Date.parse('2026-11-11T10:00:00Z');
  let online = options.online ?? true;
  const client = new MockApiClient({
    latencyMs: 0,
    now: () => time,
    isOnline: () => online,
    imageSize: () => options.imageBytes ?? 1000,
  });
  return {
    client,
    tick: (ms: number) => (time += ms),
    setOnline: (value: boolean) => (online = value),
  };
}

const upload = (client: MockApiClient, key: string, fixture: 'note-1' | 'note-2' | 'note-3' | 'garbage') =>
  client.uploadReceipt({ imageUri: 'file:///a.jpg', idempotencyKey: key, fixture });

const expectStatus = async (promise: Promise<unknown>, status: number) => {
  const error = await promise.catch((caught: unknown) => caught);
  expect(error).toBeInstanceOf(ApiError);
  expect((error as ApiError).status).toBe(status);
};

describe('MockApiClient', () => {
  it('starts CAPTURED and reaches EXTRACTED after the read finishes', async () => {
    const { client, tick } = setup();
    const { receipt, replayed } = await upload(client, 'k1', 'note-2');
    expect(replayed).toBe(false);
    expect(receipt.status).toBe('CAPTURED');
    expect(receipt.lines).toEqual([]);

    tick(5_000);
    const done = await client.getReceipt(receipt.id);
    expect(done.status).toBe('EXTRACTED');
    expect(done.lines.map((line) => line.discrepancy)).toEqual(['SHORT', 'MATCH', 'UNKNOWN_ITEM']);
    expect(done.lines[0].delta).toBe(-8);
  });

  it('returns the same receipt for a repeated key', async () => {
    const { client } = setup();
    const first = await upload(client, 'k1', 'note-1');
    const second = await upload(client, 'k1', 'note-1');
    expect(second.replayed).toBe(true);
    expect(second.receipt.id).toBe(first.receipt.id);
    expect(await client.listReceipts()).toHaveLength(1);
  });

  it('flags low confidence and adds missing PO lines as NOT_DELIVERED', async () => {
    const { client, tick } = setup();
    const { receipt } = await upload(client, 'k1', 'note-3');
    tick(20_000);
    const done = await client.getReceipt(receipt.id);
    expect(done.needsReview).toBe(true);
    expect(done.lines.map((line) => line.discrepancy)).toEqual([
      'SHORT',
      'UNIT_MISMATCH',
      'NOT_DELIVERED',
    ]);
    expect(done.lines[2].confidence).toBeNull();
  });

  it('lets the clerk resolve a NEEDS_MANUAL receipt by assigning a PO', async () => {
    const { client, tick } = setup();
    const { receipt } = await upload(client, 'k1', 'garbage');
    tick(5_000);
    const manual = await client.getReceipt(receipt.id);
    expect(manual.status).toBe('NEEDS_MANUAL');
    await expectStatus(client.confirmReceipt(receipt.id), 409);

    const assigned = await client.assignPurchaseOrder(receipt.id, 'PO-4513');
    expect(assigned.status).toBe('EXTRACTED');
    expect(assigned.lines.map((line) => line.discrepancy)).toEqual(['NOT_DELIVERED']);
  });

  it('recomputes on correction and clears the review flag', async () => {
    const { client, tick } = setup();
    const { receipt } = await upload(client, 'k1', 'note-3');
    tick(20_000);
    const corrected = await client.correctLine(receipt.id, 'RL-1', { receivedQty: 240 });
    expect(corrected.lines[0]).toMatchObject({
      discrepancy: 'MATCH',
      delta: 0,
      correctedByClerk: true,
      needsReview: false,
      aiQty: 214,
    });
  });

  it('makes decisions repeatable and rejects conflicting ones', async () => {
    const { client, tick } = setup();
    const { receipt } = await upload(client, 'k1', 'note-1');
    await expectStatus(client.confirmReceipt(receipt.id), 409);
    tick(5_000);

    await client.confirmReceipt(receipt.id);
    expect((await client.confirmReceipt(receipt.id)).status).toBe('CONFIRMED');
    await expectStatus(client.disputeReceipt(receipt.id, 'late'), 409);
    await expectStatus(client.correctLine(receipt.id, 'RL-1', { receivedQty: 1 }), 409);
  });

  it('requires a dispute reason', async () => {
    const { client, tick } = setup();
    const { receipt } = await upload(client, 'k1', 'note-1');
    tick(5_000);
    await expectStatus(client.disputeReceipt(receipt.id, '   '), 400);
  });

  it('rejects oversized images and fails like a network when offline', async () => {
    await expectStatus(upload(setup({ imageBytes: 11 * 1024 * 1024 }).client, 'k', 'note-1'), 413);
    await expect(upload(setup({ online: false }).client, 'k', 'note-1')).rejects.toBeInstanceOf(
      NetworkError,
    );
  });
});

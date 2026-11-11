import type { Receipt, ReceiptLine } from '../api/types';
import { confirmBlocker, ReviewChecks } from './decision';

const line = (id: string, needsReview: boolean) => ({ id, needsReview }) as ReceiptLine;

const receipt = (overrides: Partial<Receipt>) =>
  ({
    status: 'EXTRACTED',
    poNumber: 'PO-4503',
    needsReview: false,
    lines: [],
    ...overrides,
  }) as Receipt;

const checks = (lineIds: string[] = [], poChecked = false): ReviewChecks => ({
  lineIds: new Set(lineIds),
  poChecked,
});

describe('confirmBlocker', () => {
  it('allows a clean receipt', () => {
    expect(confirmBlocker(receipt({}), checks())).toBeNull();
  });

  it.each(['CAPTURED', 'CONFIRMED', 'DISPUTED'] as const)('blocks %s', (status) => {
    expect(confirmBlocker(receipt({ status }), checks())).not.toBeNull();
  });

  it('blocks until a PO is picked', () => {
    const blocked = receipt({ status: 'NEEDS_MANUAL', poNumber: null, needsReview: true });
    expect(confirmBlocker(blocked, checks([], true))).toBe('Pick the purchase order first.');
  });

  it('requires every flagged line and the PO to be ticked', () => {
    const flagged = receipt({
      needsReview: true,
      lines: [line('RL-1', true), line('RL-2', true), line('RL-3', false)],
    });
    expect(confirmBlocker(flagged, checks())).toContain('2 flagged lines');
    expect(confirmBlocker(flagged, checks(['RL-1']))).toContain('the flagged line');
    expect(confirmBlocker(flagged, checks(['RL-1', 'RL-2']))).toContain('purchase order');
    expect(confirmBlocker(flagged, checks(['RL-1', 'RL-2'], true))).toBeNull();
  });

  it('asks for a PO check when only the PO confidence is low', () => {
    const lowPo = receipt({ needsReview: true, lines: [line('RL-1', false)] });
    expect(confirmBlocker(lowPo, checks())).toContain('purchase order');
    expect(confirmBlocker(lowPo, checks([], true))).toBeNull();
  });
});

import type { Receipt } from '../api/types';

export interface ReviewChecks {
  lineIds: ReadonlySet<string>;
  poChecked: boolean;
}

/**
 * The server does not say why a receipt is flagged, and the threshold lives in its config,
 * so any flagged receipt with a PO asks the clerk to check the PO against the paper.
 */
export const needsPoCheck = (receipt: Receipt) => receipt.needsReview && receipt.poNumber !== null;

/** Why Confirm is unavailable, in words for the clerk, or null when it can be tapped. */
export function confirmBlocker(receipt: Receipt, checks: ReviewChecks): string | null {
  if (receipt.status !== 'EXTRACTED' && receipt.status !== 'NEEDS_MANUAL') {
    return 'This receipt cannot be confirmed now.';
  }
  if (receipt.poNumber === null) return 'Pick the purchase order first.';

  const unchecked = receipt.lines.filter(
    (line) => line.needsReview && !checks.lineIds.has(line.id),
  ).length;
  if (unchecked > 0) {
    return unchecked === 1
      ? 'Tick the flagged line after checking it against the paper.'
      : `Tick the ${unchecked} flagged lines after checking them against the paper.`;
  }
  if (needsPoCheck(receipt) && !checks.poChecked) {
    return 'Tick the purchase order after checking it against the paper.';
  }
  return null;
}

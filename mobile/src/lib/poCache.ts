import type { PurchaseOrder } from '../api/types';
import { kvGet, kvSet } from './db';
import { api } from './runtime';

const CACHE_KEY = 'open-purchase-orders';

export interface PurchaseOrderList {
  orders: PurchaseOrder[];
  /** Set when the server could not be reached and this is the last saved list. */
  cachedAt: number | null;
}

/** Fetches the list and saves it, or falls back to the saved list. Throws if there is neither. */
export async function loadOpenPurchaseOrders(): Promise<PurchaseOrderList> {
  try {
    const orders = await api.listOpenPurchaseOrders();
    await kvSet(CACHE_KEY, JSON.stringify({ orders, savedAt: Date.now() }));
    return { orders, cachedAt: null };
  } catch (error) {
    const saved = await kvGet(CACHE_KEY);
    if (!saved) throw error;
    const { orders, savedAt } = JSON.parse(saved) as { orders: PurchaseOrder[]; savedAt: number };
    return { orders, cachedAt: savedAt };
  }
}

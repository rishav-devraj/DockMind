import { apiMode, apiUrl } from '../config';
import { HttpApiClient } from './http';
import { MockApiClient, MockPersistence } from './mock';
import type {
  CorrectLineRequest,
  PurchaseOrder,
  Receipt,
  ReceiptStatus,
  UploadRequest,
  UploadResult,
} from './types';

/** The only way screens talk to the server. Numbers refer to CONTRACT.md. */
export interface ApiClient {
  /** A1 */
  listOpenPurchaseOrders(): Promise<PurchaseOrder[]>;
  /** A2 */
  listReceipts(status?: ReceiptStatus): Promise<Receipt[]>;
  /** A3 */
  getReceipt(id: string): Promise<Receipt>;
  /** A4 */
  uploadReceipt(request: UploadRequest): Promise<UploadResult>;
  /** A5 */
  correctLine(receiptId: string, lineId: string, body: CorrectLineRequest): Promise<Receipt>;
  /** A6 */
  confirmReceipt(id: string): Promise<Receipt>;
  /** A7 */
  disputeReceipt(id: string, reason: string): Promise<Receipt>;
  /** A8 */
  assignPurchaseOrder(id: string, poNumber: string): Promise<Receipt>;
  /** A9: a URI an Image can load, or null when there is no photo to show. */
  receiptImageUri(id: string): string | null;
}

export interface ApiClientDeps {
  isOnline: () => boolean;
  persistence: MockPersistence;
  imageSize: (uri: string) => number;
}

export function createApiClient(deps: ApiClientDeps): ApiClient {
  return apiMode === 'http' ? new HttpApiClient(apiUrl) : new MockApiClient(deps);
}

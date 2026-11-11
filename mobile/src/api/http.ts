import { File } from 'expo-file-system';

import type { ApiClient } from './client';
import { ApiError, NetworkError } from './errors';
import type {
  ApiErrorBody,
  CorrectLineRequest,
  PurchaseOrder,
  Receipt,
  ReceiptStatus,
  UploadRequest,
  UploadResult,
} from './types';

const DEFAULT_TIMEOUT_MS = 20_000;
const UPLOAD_TIMEOUT_MS = 60_000;

type FetchLike = (url: string, init: RequestInit) => Promise<Response>;

export class HttpApiClient implements ApiClient {
  private readonly baseUrl: string;
  private readonly fetchImpl: FetchLike;

  constructor(baseUrl: string, fetchImpl: FetchLike = (url, init) => fetch(url, init)) {
    this.baseUrl = baseUrl.replace(/\/+$/, '');
    this.fetchImpl = fetchImpl;
  }

  async listOpenPurchaseOrders(): Promise<PurchaseOrder[]> {
    return (await this.request<PurchaseOrder[]>('/purchase-orders?status=OPEN')).body;
  }

  async listReceipts(status?: ReceiptStatus): Promise<Receipt[]> {
    const query = status ? `?status=${encodeURIComponent(status)}` : '';
    return (await this.request<Receipt[]>(`/receipts${query}`)).body;
  }

  async getReceipt(id: string): Promise<Receipt> {
    return (await this.request<Receipt>(`/receipts/${encodeURIComponent(id)}`)).body;
  }

  async uploadReceipt({ imageUri, idempotencyKey, fixture }: UploadRequest): Promise<UploadResult> {
    const form = new FormData();
    // This Expo version rejects the { uri, name, type } shape, so the local file is passed as a File (a Blob).
    form.append('image', new File(imageUri), 'delivery-note.jpg');

    const headers: Record<string, string> = { 'Idempotency-Key': idempotencyKey };
    if (fixture) headers['X-Fixture'] = fixture;

    const { status, body } = await this.request<Receipt>(
      '/receipts',
      { method: 'POST', headers, body: form },
      UPLOAD_TIMEOUT_MS,
    );
    return { receipt: body, replayed: status === 200 };
  }

  async correctLine(
    receiptId: string,
    lineId: string,
    body: CorrectLineRequest,
  ): Promise<Receipt> {
    const path = `/receipts/${encodeURIComponent(receiptId)}/lines/${encodeURIComponent(lineId)}`;
    return (await this.sendJson('PATCH', path, body)).body;
  }

  async confirmReceipt(id: string): Promise<Receipt> {
    return (await this.sendJson('POST', `/receipts/${encodeURIComponent(id)}/confirm`)).body;
  }

  async disputeReceipt(id: string, reason: string): Promise<Receipt> {
    const path = `/receipts/${encodeURIComponent(id)}/dispute`;
    return (await this.sendJson('POST', path, { reason })).body;
  }

  async assignPurchaseOrder(id: string, poNumber: string): Promise<Receipt> {
    return (await this.sendJson('PATCH', `/receipts/${encodeURIComponent(id)}`, { poNumber })).body;
  }

  receiptImageUri(id: string): string {
    return `${this.baseUrl}/receipts/${encodeURIComponent(id)}/image`;
  }

  private sendJson(method: string, path: string, body?: unknown) {
    return this.request<Receipt>(path, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  }

  private async request<T>(
    path: string,
    init: RequestInit = {},
    timeoutMs = DEFAULT_TIMEOUT_MS,
  ): Promise<{ status: number; body: T }> {
    const controller = new AbortController();
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, timeoutMs);

    try {
      let response: Response;
      let text: string;
      try {
        response = await this.fetchImpl(`${this.baseUrl}${path}`, {
          ...init,
          signal: controller.signal,
        });
        text = await response.text();
      } catch (error) {
        // The real reason (DNS, TLS, connection refused, body too large) is kept in the Metro log.
        console.warn(`[api] ${init.method ?? 'GET'} ${this.baseUrl}${path} failed`, error);
        throw new NetworkError(timedOut ? 'Request timed out' : 'Network request failed', timedOut);
      }

      if (!response.ok) throw toApiError(response.status, text);

      try {
        return { status: response.status, body: JSON.parse(text) as T };
      } catch {
        throw new NetworkError('The server sent an unreadable response');
      }
    } finally {
      clearTimeout(timer);
    }
  }
}

function toApiError(status: number, text: string): ApiError {
  try {
    const body = JSON.parse(text) as Partial<ApiErrorBody>;
    if (body.message) return new ApiError(status, body.error ?? 'ERROR', body.message);
  } catch {
    // Gateways and proxies can answer with HTML. Fall through to the generic message.
  }
  return new ApiError(status, 'ERROR', `The server answered with an error (${status}).`);
}

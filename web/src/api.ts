export interface ReceiptLine {
  id: string
  poLineId: string | null
  description: string
  orderedQty: number | null
  receivedQty: number
  unit: string | null
  confidence: number | null
  discrepancy: string
  delta: number | null
  aiQty: number | null
}

export interface Receipt {
  id: string
  status: string
  poNumber: string | null
  supplier: string | null
  overallConfidence: number | null
  aiNote: string | null
  lines: ReceiptLine[]
  deliveryNoteNo: string | null
  poConfidence: number | null
  freeText: string | null
  needsReview: boolean
}

const POLL_MS = 1500
const POLL_LIMIT_MS = 120_000

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, init)
  const text = await response.text()
  let body: unknown = null
  try {
    body = JSON.parse(text)
  } catch {
    // A proxy or a sleeping host can answer with HTML. Handled below.
  }
  if (!response.ok) {
    const message = (body as { message?: string } | null)?.message
    throw new Error(message ?? `The server answered with ${response.status}.`)
  }
  if (body === null) throw new Error('The server sent an unreadable response.')
  return body as T
}

export function uploadNote(file: File): Promise<Receipt> {
  const form = new FormData()
  form.append('image', file)
  return request<Receipt>('/receipts', {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: form,
  })
}

/** Polls until the note has been read. Returns null if isCancelled turns true first. */
export async function waitUntilRead(id: string, isCancelled: () => boolean): Promise<Receipt | null> {
  const deadline = Date.now() + POLL_LIMIT_MS
  while (Date.now() < deadline) {
    if (isCancelled()) return null
    const receipt = await request<Receipt>(`/receipts/${encodeURIComponent(id)}`)
    if (receipt.status !== 'CAPTURED') return receipt
    await new Promise((resolve) => setTimeout(resolve, POLL_MS))
  }
  throw new Error('The note was still being read after 2 minutes.')
}

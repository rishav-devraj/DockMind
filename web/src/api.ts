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
  needsReview: boolean
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

export type Server = 'local' | 'hosted'

export const SERVERS: { id: Server; label: string }[] = [
  { id: 'local', label: 'Local' },
  { id: 'hosted', label: 'Render' },
]

const POLL_MS = 1500
const POLL_LIMIT_MS = 120_000

async function request<T>(server: Server, path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/${server}${path}`, init)
  const text = await response.text()
  let body: unknown = null
  try {
    body = JSON.parse(text)
  } catch {
    // A proxy or a sleeping host can answer with HTML or nothing. Handled below.
  }
  if (!response.ok) {
    const message = (body as { message?: string } | null)?.message
    if (message) throw new Error(message)
    throw new Error(
      response.status >= 500
        ? server === 'local'
          ? 'Could not reach the local server. Is it running on port 8080?'
          : 'Could not reach the Render server. It may still be waking up.'
        : `The server answered with ${response.status}.`,
    )
  }
  if (body === null) throw new Error('The server sent an unreadable response.')
  return body as T
}

export async function isUp(server: Server): Promise<boolean> {
  try {
    await request(server, '/health')
    return true
  } catch {
    return false
  }
}

export function uploadNote(server: Server, file: File): Promise<Receipt> {
  const form = new FormData()
  form.append('image', file)
  return request<Receipt>(server, '/receipts', {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: form,
  })
}

/** Polls until the note has been read. Returns null if isCancelled turns true first. */
export async function waitUntilRead(server: Server, id: string, isCancelled: () => boolean): Promise<Receipt | null> {
  const deadline = Date.now() + POLL_LIMIT_MS
  while (Date.now() < deadline) {
    if (isCancelled()) return null
    const receipt = await request<Receipt>(server, `/receipts/${encodeURIComponent(id)}`)
    if (receipt.status !== 'CAPTURED') return receipt
    await new Promise((resolve) => setTimeout(resolve, POLL_MS))
  }
  throw new Error('The note was still being read after 2 minutes.')
}

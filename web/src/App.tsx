import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent } from 'react'
import { uploadNote, waitUntilRead } from './api'
import type { Receipt } from './api'

const MAX_BYTES = 10 * 1024 * 1024

type Phase = 'idle' | 'uploading' | 'reading' | 'done' | 'error'

const percent = (value: number | null) => (value === null ? '-' : `${Math.round(value * 100)}%`)
const show = (value: string | number | null) => (value === null ? '-' : value)

export default function App() {
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [phase, setPhase] = useState<Phase>('idle')
  const [receipt, setReceipt] = useState<Receipt | null>(null)
  const [error, setError] = useState<string | null>(null)
  const currentRun = useRef(0)

  useEffect(() => {
    if (!file) return
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  const choose = (event: ChangeEvent<HTMLInputElement>) => {
    currentRun.current += 1
    setFile(event.target.files?.[0] ?? null)
    setReceipt(null)
    setError(null)
    setPhase('idle')
  }

  const read = async () => {
    if (!file) return
    if (file.size > MAX_BYTES) {
      setError('That photo is larger than the 10 MB limit.')
      setPhase('error')
      return
    }
    const run = ++currentRun.current
    const isCancelled = () => run !== currentRun.current
    setReceipt(null)
    setError(null)
    setPhase('uploading')
    try {
      const created = await uploadNote(file)
      if (isCancelled()) return
      setPhase('reading')
      const finished = await waitUntilRead(created.id, isCancelled)
      if (!finished || isCancelled()) return
      setReceipt(finished)
      setPhase('done')
    } catch (problem) {
      if (isCancelled()) return
      setError(problem instanceof Error ? problem.message : 'Something went wrong.')
      setPhase('error')
    }
  }

  const busy = phase === 'uploading' || phase === 'reading'

  return (
    <main className="mx-auto max-w-5xl space-y-4 p-4">
      <h1 className="text-xl font-bold">DockMind OCR test</h1>

      <div className="flex flex-wrap items-center gap-3">
        <input type="file" accept="image/jpeg,image/png" onChange={choose} />
        <button className="border px-3 py-1 disabled:opacity-50" disabled={!file || busy} onClick={read}>
          Read note
        </button>
        {phase === 'uploading' && <span>Uploading...</span>}
        {phase === 'reading' && <span>Reading the note...</span>}
      </div>

      {error && <p role="alert" className="text-red-700">{error}</p>}

      <div className="grid gap-4 md:grid-cols-2">
        {file && preview && <img src={preview} alt="Selected delivery note" className="w-full border" />}
        {receipt && <Result receipt={receipt} />}
      </div>

      {receipt && <Lines receipt={receipt} />}

      {receipt && (
        <details>
          <summary>Raw response</summary>
          <pre className="overflow-x-auto text-xs">{JSON.stringify(receipt, null, 2)}</pre>
        </details>
      )}
    </main>
  )
}

function Result({ receipt }: { receipt: Receipt }) {
  const fields: [string, string | number | null][] = [
    ['Receipt', receipt.id],
    ['Status', receipt.status],
    ['Supplier', receipt.supplier],
    ['PO', receipt.poNumber],
    ['PO confidence', percent(receipt.poConfidence)],
    ['Delivery note', receipt.deliveryNoteNo],
    ['Overall confidence', percent(receipt.overallConfidence)],
    ['Needs review', receipt.needsReview ? 'Yes' : 'No'],
    ['Other writing', receipt.freeText],
    ['AI note', receipt.aiNote],
  ]
  return (
    <dl className="space-y-1">
      {fields.map(([label, value]) => (
        <div key={label}>
          <dt className="inline font-semibold">{label}: </dt>
          <dd className="inline">{show(value)}</dd>
        </div>
      ))}
    </dl>
  )
}

function Lines({ receipt }: { receipt: Receipt }) {
  if (receipt.lines.length === 0) return <p>No lines were read from this note.</p>
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b">
            <th className="p-1">Item</th>
            <th className="p-1">Qty read</th>
            <th className="p-1">Unit</th>
            <th className="p-1">Confidence</th>
            <th className="p-1">Ordered</th>
            <th className="p-1">Result</th>
          </tr>
        </thead>
        <tbody>
          {receipt.lines.map((line) => (
            <tr key={line.id} className="border-b">
              <td className="p-1">{line.description}</td>
              <td className="p-1">{line.aiQty === null ? 'Not on note' : line.aiQty}</td>
              <td className="p-1">{show(line.unit)}</td>
              <td className="p-1">{percent(line.confidence)}</td>
              <td className="p-1">{show(line.orderedQty)}</td>
              <td className="p-1">{line.discrepancy}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

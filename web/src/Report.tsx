import type { ReactNode } from 'react'
import type { Receipt, ReceiptLine } from './api'

const REVIEW_THRESHOLD = 0.8

const RESULTS: Record<string, { label: string; text: string; bar: string }> = {
  SHORT: { label: 'Short', text: 'text-short', bar: 'bg-short' },
  OVER: { label: 'Over', text: 'text-over', bar: 'bg-over' },
  UNIT_MISMATCH: { label: 'Wrong unit', text: 'text-unit', bar: 'bg-unit' },
  UNKNOWN_ITEM: { label: 'Not ordered', text: 'text-unknown', bar: 'bg-unknown' },
  NOT_DELIVERED: { label: 'Not delivered', text: 'text-missing', bar: 'bg-missing' },
  MATCH: { label: 'Match', text: 'text-match', bar: 'bg-match' },
}

const LINE_GRID = 'sm:grid-cols-[minmax(0,1fr)_6.5rem_4.5rem_4rem_4.5rem_7.5rem]'

const percent = (value: number | null) => (value === null ? '-' : `${Math.round(value * 100)}%`)

function resultOf(line: ReceiptLine, unresolved: boolean) {
  const result = RESULTS[line.discrepancy] ?? { label: line.discrepancy, text: 'text-unknown', bar: 'bg-unknown' }
  return unresolved && line.discrepancy === 'UNKNOWN_ITEM' ? { ...result, label: 'No PO yet' } : result
}

function verdict(receipt: Receipt): string {
  const total = receipt.lines.length
  if (receipt.status === 'NEEDS_MANUAL') return total === 0 ? 'Could not read this note' : 'Needs a clerk'
  const off = receipt.lines.filter((line) => line.discrepancy !== 'MATCH').length
  if (off === 0) return 'Matches the order'
  return `${off} of ${total} ${total === 1 ? 'line' : 'lines'} off`
}

function caution(receipt: Receipt): string | null {
  if (receipt.status === 'NEEDS_MANUAL') {
    return receipt.lines.length === 0
      ? 'Nothing usable came back from the reader. If this repeats, look for "Extraction failed" in the server logs.'
      : 'The PO could not be confirmed, or a line could not be read.'
  }
  return receipt.needsReview ? 'Part of this note was read with low confidence. Check it against the photo.' : null
}

export function Eyebrow({ children }: { children: ReactNode }) {
  return <p className="font-mono text-xs tracking-wider text-muted uppercase">{children}</p>
}

export default function Report({ receipt }: { receipt: Receipt }) {
  const unresolved = receipt.poNumber === null
  const warning = caution(receipt)

  return (
    <article>
      <Eyebrow>
        {receipt.id} / {receipt.status.replace('_', ' ')}
      </Eyebrow>
      <h2 className="mt-3 text-5xl font-semibold tracking-tight">{verdict(receipt)}</h2>
      {warning && <p className="mt-4 inline-block bg-flag px-3 py-2 text-sm">{warning}</p>}

      <dl className="mt-8 grid grid-cols-2 gap-y-5 border-y border-rule py-5 sm:grid-cols-[2fr_1fr_1fr_1fr_1fr]">
        <Fact label="Supplier" value={receipt.supplier ?? '-'} />
        <Fact label="PO" value={receipt.poNumber ?? 'Unresolved'} mono />
        <Fact label="PO conf." value={percent(receipt.poConfidence)} mono low={isLow(receipt.poConfidence)} />
        <Fact label="Note no." value={receipt.deliveryNoteNo ?? '-'} mono />
        <Fact label="Overall" value={percent(receipt.overallConfidence)} mono low={isLow(receipt.overallConfidence)} />
      </dl>

      {receipt.aiNote && (
        <div className="mt-6 max-w-2xl">
          <Eyebrow>Reader's note</Eyebrow>
          <p className="mt-2 leading-relaxed">{receipt.aiNote}</p>
        </div>
      )}
      {receipt.freeText && (
        <div className="mt-5 max-w-2xl">
          <Eyebrow>Also written on the note</Eyebrow>
          <p className="mt-2">{receipt.freeText}</p>
        </div>
      )}

      {receipt.lines.length > 0 && (
        <div className="mt-9">
          <div className={`hidden gap-3 border-b-2 border-ink pr-2 pb-2 pl-4 sm:grid ${LINE_GRID}`}>
            <Eyebrow>Item</Eyebrow>
            <Head>Read</Head>
            <Head>Ordered</Head>
            <Head>Diff</Head>
            <Head>Conf.</Head>
            <Eyebrow>Result</Eyebrow>
          </div>
          <ul className="border-t-2 border-ink sm:border-t-0">
            {receipt.lines.map((line) => (
              <Line key={line.id} line={line} unresolved={unresolved} />
            ))}
          </ul>
        </div>
      )}

      <details className="mt-8 border-t border-rule pt-4">
        <summary className="cursor-pointer font-mono text-xs tracking-wider text-muted uppercase">Raw response</summary>
        <pre className="mt-3 overflow-x-auto font-mono text-xs text-muted">{JSON.stringify(receipt, null, 2)}</pre>
      </details>
    </article>
  )
}

const isLow = (value: number | null) => value !== null && value < REVIEW_THRESHOLD

function Head({ children }: { children: ReactNode }) {
  return <p className="text-right font-mono text-xs tracking-wider text-muted uppercase">{children}</p>
}

function Fact({ label, value, mono, low }: { label: string; value: string; mono?: boolean; low?: boolean }) {
  return (
    <div className="min-w-0 pr-4">
      <dt className="font-mono text-xs tracking-wider text-muted uppercase">{label}</dt>
      <dd className={`mt-1 truncate font-medium ${mono ? 'font-mono text-sm' : ''} ${low ? 'text-over' : ''}`}>{value}</dd>
    </div>
  )
}

function Line({ line, unresolved }: { line: ReceiptLine; unresolved: boolean }) {
  const result = resultOf(line, unresolved)
  const delta = line.delta === null ? '-' : line.delta > 0 ? `+${line.delta}` : String(line.delta)
  const read = line.aiQty === null ? 'Not on note' : `${line.aiQty}${line.unit ? ` ${line.unit}` : ''}`
  return (
    <li
      data-line
      className={`relative grid grid-cols-2 items-baseline gap-x-3 gap-y-1 border-b border-rule py-4 pr-2 pl-4 ${LINE_GRID} ${line.needsReview ? 'bg-flag/60' : ''}`}
    >
      <span className={`absolute inset-y-0 left-0 w-1 ${result.bar}`} />
      <p className="col-span-2 font-medium sm:col-span-1">{line.description}</p>
      <Cell label="Read" muted={line.aiQty === null}>{read}</Cell>
      <Cell label="Ordered">{line.orderedQty ?? '-'}</Cell>
      <Cell label="Diff">{delta}</Cell>
      <Cell label="Conf." warn={isLow(line.confidence)}>{percent(line.confidence)}</Cell>
      <p className={`col-span-2 text-sm font-semibold sm:col-span-1 ${result.text}`}>{result.label}</p>
    </li>
  )
}

function Cell({ label, children, muted, warn }: { label: string; children: ReactNode; muted?: boolean; warn?: boolean }) {
  return (
    <p className={`flex justify-between gap-2 font-mono text-sm sm:block sm:text-right ${muted ? 'text-muted' : ''} ${warn ? 'font-medium text-over' : ''}`}>
      <span className="text-xs text-muted sm:hidden">{label}</span>
      {children}
    </p>
  )
}

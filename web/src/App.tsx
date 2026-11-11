import { useEffect, useRef, useState } from 'react'
import type { DragEvent } from 'react'
import { isUp, SERVERS, uploadNote, waitUntilRead } from './api'
import type { Receipt, Server } from './api'
import Report, { Eyebrow } from './Report'

const MAX_BYTES = 10 * 1024 * 1024
const ACCEPTED = ['image/jpeg', 'image/png']
const SERVER_KEY = 'dockmind-server'

type Phase = 'idle' | 'uploading' | 'reading' | 'done' | 'error'

function savedServer(): Server {
  try {
    return localStorage.getItem(SERVER_KEY) === 'hosted' ? 'hosted' : 'local'
  } catch {
    return 'local'
  }
}

export default function App() {
  const [server, setServer] = useState<Server>(savedServer)
  const [online, setOnline] = useState<boolean | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [phase, setPhase] = useState<Phase>('idle')
  const [receipt, setReceipt] = useState<Receipt | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const currentRun = useRef(0)

  useEffect(() => {
    if (!file) return
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  useEffect(() => {
    let stale = false
    isUp(server).then((up) => {
      if (!stale) setOnline(up)
    })
    return () => {
      stale = true
    }
  }, [server])

  const switchServer = (next: Server) => {
    if (next === server) return
    currentRun.current += 1
    setServer(next)
    setOnline(null)
    setReceipt(null)
    setError(null)
    setPhase('idle')
    try {
      localStorage.setItem(SERVER_KEY, next)
    } catch {
      // The choice just won't be remembered.
    }
  }

  const fail = (message: string) => {
    setError(message)
    setPhase('error')
  }

  const select = (next: File | undefined) => {
    if (!next) return
    currentRun.current += 1
    setReceipt(null)
    setError(null)
    setPhase('idle')
    if (!ACCEPTED.includes(next.type)) return fail('Choose a JPEG or PNG photo.')
    if (next.size > MAX_BYTES) return fail('That photo is larger than the 10 MB limit.')
    setFile(next)
  }

  const drop = (event: DragEvent) => {
    event.preventDefault()
    setDragging(false)
    select(event.dataTransfer.files[0])
  }

  const read = async () => {
    if (!file) return
    const run = ++currentRun.current
    const isCancelled = () => run !== currentRun.current
    setReceipt(null)
    setError(null)
    setPhase('uploading')
    try {
      const created = await uploadNote(server, file)
      if (isCancelled()) return
      setPhase('reading')
      const finished = await waitUntilRead(server, created.id, isCancelled)
      if (!finished || isCancelled()) return
      setReceipt(finished)
      setPhase('done')
    } catch (problem) {
      if (!isCancelled()) fail(problem instanceof Error ? problem.message : 'Something went wrong.')
    }
  }

  const busy = phase === 'uploading' || phase === 'reading'

  return (
    <div className="grid min-h-screen lg:h-screen lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
      <section className="grid grid-rows-[auto_minmax(0,1fr)_auto] bg-bench text-white lg:h-screen">
        <header className="flex flex-wrap items-center gap-x-3 gap-y-2 border-b border-bench-line px-6 py-3">
          <h1 className="font-mono text-sm font-medium tracking-[0.2em] text-hivis">DOCKMIND</h1>
          <p className="font-mono text-xs tracking-wider text-bench-muted">OCR BENCH</p>
          <div className="ml-auto flex items-center gap-3">
            <p role="status" className="flex items-center gap-2 font-mono text-xs text-bench-muted">
              <span
                className={`size-2 rounded-full ${online === null ? 'bg-bench-muted' : online ? 'bg-match' : 'bg-short'}`}
              />
              {online === null ? 'Checking' : online ? 'Online' : 'Offline'}
            </p>
            <div role="group" aria-label="Server" className="flex border border-bench-line">
              {SERVERS.map(({ id, label }) => (
                <button
                  key={id}
                  aria-pressed={server === id}
                  onClick={() => switchServer(id)}
                  className={`cursor-pointer px-3 py-1 font-mono text-xs transition-colors ${
                    server === id ? 'bg-hivis text-bench' : 'text-bench-muted hover:text-white'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        </header>

        <label
          onDragOver={(event) => {
            event.preventDefault()
            setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={drop}
          className="group flex min-h-[60vw] cursor-pointer items-center justify-center p-6 lg:min-h-0"
        >
          <input
            type="file"
            aria-label="Delivery note photo"
            accept={ACCEPTED.join(',')}
            className="peer sr-only"
            onChange={(event) => {
              select(event.target.files?.[0])
              event.target.value = ''
            }}
          />
          {file && preview ? (
            <div className="relative">
              <img
                src={preview}
                alt="Selected delivery note"
                className={`max-h-[60vh] max-w-full transition-opacity lg:max-h-[calc(100vh-11rem)] ${dragging ? 'opacity-40' : ''}`}
              />
              {busy && <div className="scanline" />}
            </div>
          ) : (
            <Viewfinder active={dragging} />
          )}
        </label>

        <footer className="flex items-center gap-4 border-t border-bench-line px-6 py-4">
          <p className="min-w-0 flex-1 truncate font-mono text-xs text-bench-muted">
            {file ? file.name : 'No photo selected'}
          </p>
          <button
            onClick={read}
            disabled={!file || busy}
            className="shrink-0 cursor-pointer bg-hivis px-6 py-3 text-sm font-semibold text-bench transition-colors hover:bg-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-hivis disabled:cursor-not-allowed disabled:bg-bench-line disabled:text-bench-muted"
          >
            {busy ? 'Reading...' : 'Read note'}
          </button>
        </footer>
      </section>

      <section className="min-w-0 bg-paper px-6 py-8 sm:px-10 lg:h-screen lg:overflow-y-auto lg:py-10">
        {phase === 'idle' && <Reference />}
        {busy && <Reading phase={phase} />}
        {phase === 'error' && (
          <div role="alert" className="border-l-4 border-short pl-5">
            <Eyebrow>Failed</Eyebrow>
            <p className="mt-2 text-3xl font-semibold tracking-tight">That did not work</p>
            <p className="mt-3 text-muted">{error}</p>
          </div>
        )}
        {phase === 'done' && receipt && <Report receipt={receipt} />}
      </section>
    </div>
  )
}

function Viewfinder({ active }: { active: boolean }) {
  const corner = `absolute size-8 transition-colors ${active ? 'border-hivis' : 'border-bench-muted group-hover:border-hivis group-has-[:focus-visible]:border-hivis'}`
  return (
    <div className="relative flex h-full min-h-[50vw] w-full items-center justify-center lg:min-h-0">
      <span className={`${corner} top-0 left-0 border-t-2 border-l-2`} />
      <span className={`${corner} top-0 right-0 border-t-2 border-r-2`} />
      <span className={`${corner} bottom-0 left-0 border-b-2 border-l-2`} />
      <span className={`${corner} right-0 bottom-0 border-r-2 border-b-2`} />
      <div className="px-8 text-center">
        <p className="text-2xl font-medium tracking-tight">Drop a delivery note</p>
        <p className="mt-2 text-sm text-bench-muted">or click to choose a JPEG or PNG, up to 10 MB</p>
      </div>
    </div>
  )
}

const TEST_NOTES = [
  ['note-1-clean', 'Fresh Farm, PO-4501. All three lines should match.'],
  ['note-2-short', 'Blue Ocean, PO-4502. Salmon 8 kg short, scallops not ordered, prawns missing.'],
  ['note-3-messy', 'Dairy Direct, smudged PO, which must not be read as PO-4513. Quantities 252 EA, 100 CASE and 38 KG.'],
  ['note-4-over', 'Fresh Farm, PO-4501. Apples 20 kg over, the rest match despite KGS and CASES.'],
  ['note-5-eggs', 'Green Valley, PO-4513. Matches. The one-digit sibling of PO-4503.'],
  ['note-6-wrong-supplier', 'Eggs supplier printing PO-4503, a dairy PO. Should need a clerk.'],
  ['note-7-no-po', 'Blue Ocean with no PO number. Should need a clerk.'],
  ['note-8-crossed-out', 'Blue Ocean, PO-4502. Salmon 80 struck out, must read 78. Prawns match.'],
  ['note-9-not-a-note', 'A cafe receipt. Should need a clerk, never match a PO.'],
]

function Reference() {
  return (
    <div className="max-w-xl">
      <Eyebrow>Nothing read yet</Eyebrow>
      <p className="mt-3 text-4xl font-semibold tracking-tight">Check the reader against the paper.</p>
      <p className="mt-4 text-muted">
        Upload a note and compare the photo with what comes back. These test notes in
        <span className="font-mono text-sm"> kit/delivery-notes</span> have known answers.
      </p>
      <dl className="mt-8 border-t border-rule">
        {TEST_NOTES.map(([name, expected]) => (
          <div key={name} className="grid gap-1 border-b border-rule py-4 sm:grid-cols-[13rem_1fr] sm:gap-4">
            <dt className="font-mono text-sm">{name}</dt>
            <dd className="text-sm text-muted">{expected}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}

function Reading({ phase }: { phase: Phase }) {
  return (
    <div aria-live="polite">
      <Eyebrow>{phase === 'uploading' ? 'Uploading' : 'In progress'}</Eyebrow>
      <p className="mt-3 text-4xl font-semibold tracking-tight">
        {phase === 'uploading' ? 'Uploading the photo' : 'Reading the note'}
      </p>
      <p className="mt-4 text-muted">This can take up to a minute if the server was asleep.</p>
      <div className="mt-10 animate-pulse border-t border-rule motion-reduce:animate-none">
        {[0, 1, 2, 3].map((row) => (
          <div key={row} className="flex items-center gap-8 border-b border-rule py-5">
            <div className="h-3 w-1/3 bg-rule" />
            <div className="ml-auto h-3 w-12 bg-rule" />
            <div className="h-3 w-12 bg-rule" />
            <div className="h-3 w-20 bg-rule" />
          </div>
        ))}
      </div>
    </div>
  )
}

import { useEffect, useRef, useState } from 'react'
import { deleteStatement, getStatements, getTransactions, processStatement, reviewStatement, uploadStatement } from '../api/statements'
import { createCashEntry, deleteCashEntry, getCashEntries } from '../api/cash'

const BANK_DISPLAY  = { CAPITAL_ONE: 'Capital One', REGIONS: 'Regions' }
const TYPE_COLORS   = {
  CREDIT:   'bg-rose-500/10 text-rose-400 border-rose-500/20',
  CHECKING: 'bg-sky-500/10 text-sky-400 border-sky-500/20',
  SAVINGS:  'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
}
const TX_TYPE_COLORS = {
  CREDIT: 'bg-emerald-500/10 text-emerald-400',
  DEBIT:  'bg-red-500/10 text-red-400',
}

const CATEGORIES = [
  'Food & Dining', 'Transport', 'Entertainment', 'Shopping',
  'Utilities', 'Health', 'Travel', 'Education', 'Other',
]

function periodLabel(month, year) {
  return new Date(year, month - 1).toLocaleString('en-US', { month: 'long', year: 'numeric' })
}

export default function StatementsPage() {
  const [statements, setStatements]         = useState([])
  const [loading, setLoading]               = useState(true)
  const [uploadFile, setUploadFile]         = useState(null)
  const [uploading, setUploading]           = useState(false)
  const [uploadError, setUploadError]       = useState('')
  const [dragOver, setDragOver]             = useState(false)
  const [expandedId, setExpandedId]         = useState(null)
  const [transactions, setTransactions]     = useState({})
  const [loadingTx, setLoadingTx]           = useState(null)
  const [processingId, setProcessingId]     = useState(null)
  // Review modal
  const [reviewModal, setReviewModal]       = useState(null) // { statementId, items }
  const [reviewAnswers, setReviewAnswers]   = useState({})  // transactionId → true/false/null
  const [submittingReview, setSubmittingReview] = useState(false)
  // Cash modal
  const [showCashModal, setShowCashModal]   = useState(false)
  const [cashEntries, setCashEntries]       = useState([])
  const [cashForm, setCashForm]             = useState({ date: '', description: '', amount: '', type: 'IN', category: '' })
  const [savingCash, setSavingCash]         = useState(false)
  const fileInputRef = useRef(null)

  useEffect(() => { loadStatements(); loadCashEntries() }, [])

  async function loadStatements() {
    try {
      setLoading(true)
      setStatements(await getStatements())
    } catch { /* silent */ }
    finally { setLoading(false) }
  }

  async function loadCashEntries() {
    try { setCashEntries(await getCashEntries()) } catch { /* silent */ }
  }

  // ── Upload ────────────────────────────────────────────────────────────────
  function handleDrop(e) {
    e.preventDefault()
    setDragOver(false)
    const file = e.dataTransfer.files[0]
    if (file) pickFile(file)
  }

  function pickFile(file) {
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      setUploadError('Only PDF files are accepted.')
      return
    }
    setUploadError('')
    setUploadFile(file)
  }

  async function handleUpload() {
    if (!uploadFile) return
    setUploading(true)
    setUploadError('')
    try {
      const created = await uploadStatement(uploadFile)
      setStatements((prev) => [created, ...prev])
      setUploadFile(null)
    } catch (err) {
      setUploadError(err.message || 'Upload failed')
    } finally {
      setUploading(false)
    }
  }

  // ── Process ───────────────────────────────────────────────────────────────
  async function handleProcess(id) {
    setProcessingId(id)
    try {
      const updated = await processStatement(id)
      setStatements((prev) => prev.map((s) => (s.id === id ? updated : s)))
      await loadTransactionsFor(id)
      setExpandedId(id)

      if (updated.pendingReviews && updated.pendingReviews.length > 0) {
        // Key by index since each item is already a deduplicated group
        const initialAnswers = {}
        updated.pendingReviews.forEach((_, i) => { initialAnswers[i] = null })
        setReviewAnswers(initialAnswers)
        setReviewModal({ statementId: id, items: updated.pendingReviews })
      }
    } catch (err) {
      alert(err.message || 'Processing failed')
    } finally {
      setProcessingId(null)
    }
  }

  // ── Review modal ──────────────────────────────────────────────────────────
  async function handleReviewSubmit() {
    if (!reviewModal) return
    const answers = Object.entries(reviewAnswers)
      .filter(([, v]) => v !== null)
      .map(([idx, isIncome]) => ({
        transactionIds: reviewModal.items[Number(idx)].transactionIds,
        isIncome,
      }))

    setSubmittingReview(true)
    try {
      await reviewStatement(reviewModal.statementId, answers)
      setReviewModal(null)
      setReviewAnswers({})
      // Refresh transactions for that statement
      await loadTransactionsFor(reviewModal.statementId)
    } catch (err) {
      alert(err.message || 'Failed to submit review')
    } finally {
      setSubmittingReview(false)
    }
  }

  // ── Cash entry ────────────────────────────────────────────────────────────
  async function handleSaveCash() {
    if (!cashForm.date || !cashForm.description || !cashForm.amount) return
    setSavingCash(true)
    try {
      const entry = await createCashEntry({
        date: cashForm.date,
        description: cashForm.description,
        amount: parseFloat(cashForm.amount),
        type: cashForm.type,
        category: cashForm.category || null,
      })
      setCashEntries((prev) => [entry, ...prev])
      setCashForm({ date: '', description: '', amount: '', type: 'IN', category: '' })
      setShowCashModal(false)
    } catch (err) {
      alert(err.message || 'Failed to save cash entry')
    } finally {
      setSavingCash(false)
    }
  }

  async function handleDeleteCash(id) {
    try {
      await deleteCashEntry(id)
      setCashEntries((prev) => prev.filter((e) => e.id !== id))
    } catch { /* silent */ }
  }

  // ── Delete ────────────────────────────────────────────────────────────────
  async function handleDelete(id) {
    if (!window.confirm('Delete this statement and all its transactions?')) return
    try {
      await deleteStatement(id)
      setStatements((prev) => prev.filter((s) => s.id !== id))
      if (expandedId === id) setExpandedId(null)
    } catch { /* silent */ }
  }

  // ── Transactions expand ───────────────────────────────────────────────────
  async function toggleExpand(id, isProcessed) {
    if (!isProcessed) return
    if (expandedId === id) { setExpandedId(null); return }
    setExpandedId(id)
    if (!transactions[id]) await loadTransactionsFor(id)
  }

  async function loadTransactionsFor(id) {
    setLoadingTx(id)
    try {
      const txs = await getTransactions(id)
      setTransactions((prev) => ({ ...prev, [id]: txs }))
    } catch { /* silent */ }
    finally { setLoadingTx(null) }
  }

  return (
    <div className="p-8">
      {/* Header */}
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-white text-2xl font-bold">Statements</h1>
          <p className="text-white/40 text-sm mt-1">Upload and process your bank statements</p>
        </div>
        <button
          onClick={() => setShowCashModal(true)}
          className="flex items-center gap-2 px-4 py-2 rounded-xl bg-white/[0.04] border border-white/10 text-white/60 hover:text-white/80 text-sm transition-colors"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M12 4v16m8-8H4" />
          </svg>
          Record Cash
        </button>
      </div>

      {/* Upload zone */}
      <div
        onDragOver={(e) => { e.preventDefault(); setDragOver(true) }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        className={`mb-8 border-2 border-dashed rounded-2xl p-8 text-center transition-colors duration-200 ${
          dragOver
            ? 'border-brand-500/60 bg-brand-500/5'
            : uploadFile
            ? 'border-brand-500/30 bg-brand-500/[0.03]'
            : 'border-white/10 bg-white/[0.02]'
        }`}
      >
        <div className="w-10 h-10 rounded-xl bg-brand-950 border border-brand-800/40 flex items-center justify-center mx-auto mb-3">
          <svg className="w-5 h-5 text-brand-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
          </svg>
        </div>

        {uploadFile ? (
          <div className="flex flex-col items-center gap-3">
            <div className="flex items-center gap-2 bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2">
              <svg className="w-4 h-4 text-brand-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <span className="text-white/70 text-sm">{uploadFile.name}</span>
              <button onClick={() => setUploadFile(null)} className="text-white/30 hover:text-white/60 ml-1">
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <button
              onClick={handleUpload}
              disabled={uploading}
              className="px-5 py-2 rounded-xl bg-brand-600 hover:bg-brand-500 disabled:opacity-50 text-white text-sm font-medium transition-colors"
            >
              {uploading ? (
                <span className="flex items-center gap-2">
                  <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  Uploading…
                </span>
              ) : 'Upload Statement'}
            </button>
          </div>
        ) : (
          <>
            <p className="text-white/50 text-sm mb-1">Drag &amp; drop a PDF statement here</p>
            <p className="text-white/25 text-xs mb-4">or</p>
            <button
              onClick={() => fileInputRef.current?.click()}
              className="px-4 py-2 rounded-xl bg-white/[0.04] border border-white/10 text-white/50 hover:text-white/70 text-sm transition-colors"
            >
              Browse file
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept=".pdf"
              className="hidden"
              onChange={(e) => { if (e.target.files[0]) pickFile(e.target.files[0]) }}
            />
          </>
        )}

        {uploadError && (
          <p className="mt-3 text-red-400 text-sm bg-red-500/10 border border-red-500/20 rounded-xl px-3 py-2 max-w-sm mx-auto">
            {uploadError}
          </p>
        )}
      </div>

      {/* Cash entries */}
      {cashEntries.length > 0 && (
        <div className="mb-8">
          <h2 className="text-white/50 text-xs font-medium uppercase tracking-wide mb-3">Cash Entries</h2>
          <div className="space-y-2">
            {cashEntries.map((c) => (
              <div key={c.id} className="flex items-center gap-4 bg-white/[0.02] border border-white/5 rounded-xl px-4 py-2.5">
                <span className="text-white/40 text-xs tabular-nums w-24 shrink-0">
                  {new Date(c.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                </span>
                <span className="text-white/70 text-xs flex-1 truncate">{c.description}</span>
                {c.category && <span className="text-white/30 text-xs">{c.category}</span>}
                <span className={`text-xs font-medium tabular-nums ${c.type === 'IN' ? 'text-emerald-400' : 'text-white/70'}`}>
                  {c.type === 'IN' ? '+' : '-'}${Number(c.amount).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                </span>
                <button
                  onClick={() => handleDeleteCash(c.id)}
                  className="p-1 text-white/20 hover:text-red-400 transition-colors"
                >
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Statements list */}
      {loading ? (
        <div className="flex items-center justify-center h-40">
          <div className="w-6 h-6 border-2 border-brand-500 border-t-transparent rounded-full animate-spin" />
        </div>
      ) : statements.length === 0 ? (
        <div className="bg-white/[0.03] border border-white/5 border-dashed rounded-2xl p-12 text-center">
          <p className="text-white/30 text-sm">No statements uploaded yet.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {statements.map((s) => (
            <StatementCard
              key={s.id}
              statement={s}
              expanded={expandedId === s.id}
              transactions={transactions[s.id]}
              loadingTx={loadingTx === s.id}
              processing={processingId === s.id}
              onToggle={() => toggleExpand(s.id, s.processed)}
              onProcess={() => handleProcess(s.id)}
              onDelete={() => handleDelete(s.id)}
            />
          ))}
        </div>
      )}

      {/* ── Review Modal ──────────────────────────────────────────────────── */}
      {reviewModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="bg-[#111] border border-white/10 rounded-2xl w-full max-w-lg shadow-2xl">
            <div className="px-6 pt-6 pb-4 border-b border-white/5">
              <h2 className="text-white font-semibold text-lg">Review Transactions</h2>
              <p className="text-white/40 text-sm mt-1">
                We found {reviewModal.items.length} unrecognized credit{reviewModal.items.length !== 1 ? 's' : ''}.
                Tell us which are income.
              </p>
            </div>

            <div className="px-6 py-4 max-h-[50vh] overflow-y-auto space-y-4">
              {reviewModal.items.map((item, idx) => (
                <div key={idx} className="bg-white/[0.03] border border-white/5 rounded-xl p-4">
                  <div className="flex items-start justify-between gap-3 mb-3">
                    <div className="min-w-0">
                      <p className="text-white/80 text-sm truncate" title={item.description}>{item.description}</p>
                      {item.count > 1 && (
                        <p className="text-white/30 text-xs mt-0.5">{item.count} transactions · total ${Number(item.totalAmount).toLocaleString('en-US', { minimumFractionDigits: 2 })}</p>
                      )}
                    </div>
                    <span className="text-emerald-400 text-sm font-medium tabular-nums shrink-0">
                      +${Number(item.totalAmount).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                    </span>
                  </div>
                  <p className="text-white/40 text-xs mb-2">Is this income?</p>
                  <div className="flex gap-2">
                    <button
                      onClick={() => setReviewAnswers((prev) => ({ ...prev, [idx]: true }))}
                      className={`flex-1 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
                        reviewAnswers[idx] === true
                          ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-400'
                          : 'bg-white/[0.04] border-white/10 text-white/50 hover:text-white/70'
                      }`}
                    >
                      Yes, income
                    </button>
                    <button
                      onClick={() => setReviewAnswers((prev) => ({ ...prev, [idx]: false }))}
                      className={`flex-1 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
                        reviewAnswers[idx] === false
                          ? 'bg-white/10 border-white/20 text-white/70'
                          : 'bg-white/[0.04] border-white/10 text-white/50 hover:text-white/70'
                      }`}
                    >
                      No
                    </button>
                  </div>
                </div>
              ))}
            </div>

            <div className="px-6 pt-4 pb-6 border-t border-white/5 flex items-center justify-between gap-3">
              <p className="text-white/30 text-xs">
                {Object.values(reviewAnswers).filter((v) => v !== null).length} of {reviewModal.items.length} answered
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => { setReviewModal(null); setReviewAnswers({}) }}
                  className="px-4 py-2 rounded-xl bg-white/[0.04] border border-white/10 text-white/50 hover:text-white/70 text-sm transition-colors"
                >
                  Skip
                </button>
                <button
                  onClick={handleReviewSubmit}
                  disabled={submittingReview || Object.values(reviewAnswers).every((v) => v === null)}
                  className="px-4 py-2 rounded-xl bg-brand-600 hover:bg-brand-500 disabled:opacity-40 text-white text-sm font-medium transition-colors"
                >
                  {submittingReview ? (
                    <span className="flex items-center gap-2">
                      <span className="w-3.5 h-3.5 border border-white/30 border-t-white rounded-full animate-spin" />
                      Saving…
                    </span>
                  ) : 'Save Answers'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── Cash Entry Modal ─────────────────────────────────────────────── */}
      {showCashModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="bg-[#111] border border-white/10 rounded-2xl w-full max-w-sm shadow-2xl">
            <div className="px-6 pt-6 pb-4 border-b border-white/5">
              <h2 className="text-white font-semibold text-lg">Record Cash Transaction</h2>
            </div>

            <div className="px-6 py-4 space-y-4">
              {/* Type toggle */}
              <div className="flex gap-2">
                {['IN', 'OUT'].map((t) => (
                  <button
                    key={t}
                    onClick={() => setCashForm((f) => ({ ...f, type: t }))}
                    className={`flex-1 py-2 rounded-xl text-sm font-medium border transition-colors ${
                      cashForm.type === t
                        ? t === 'IN'
                          ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-400'
                          : 'bg-red-500/10 border-red-500/20 text-red-400'
                        : 'bg-white/[0.04] border-white/10 text-white/40 hover:text-white/60'
                    }`}
                  >
                    {t === 'IN' ? 'Income' : 'Expense'}
                  </button>
                ))}
              </div>

              {/* Date */}
              <div>
                <label className="text-white/40 text-xs block mb-1.5">Date</label>
                <input
                  type="date"
                  value={cashForm.date}
                  onChange={(e) => setCashForm((f) => ({ ...f, date: e.target.value }))}
                  className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2 text-white/80 text-sm focus:outline-none focus:border-brand-500/50"
                />
              </div>

              {/* Description */}
              <div>
                <label className="text-white/40 text-xs block mb-1.5">Description</label>
                <input
                  type="text"
                  value={cashForm.description}
                  onChange={(e) => setCashForm((f) => ({ ...f, description: e.target.value }))}
                  placeholder="e.g. Freelance payment"
                  className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2 text-white/80 text-sm placeholder-white/20 focus:outline-none focus:border-brand-500/50"
                />
              </div>

              {/* Amount */}
              <div>
                <label className="text-white/40 text-xs block mb-1.5">Amount</label>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-white/30 text-sm">$</span>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={cashForm.amount}
                    onChange={(e) => setCashForm((f) => ({ ...f, amount: e.target.value }))}
                    placeholder="0.00"
                    className="w-full bg-white/[0.04] border border-white/10 rounded-xl pl-6 pr-3 py-2 text-white/80 text-sm placeholder-white/20 focus:outline-none focus:border-brand-500/50"
                  />
                </div>
              </div>

              {/* Category */}
              <div>
                <label className="text-white/40 text-xs block mb-1.5">Category (optional)</label>
                <select
                  value={cashForm.category}
                  onChange={(e) => setCashForm((f) => ({ ...f, category: e.target.value }))}
                  className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2 text-white/80 text-sm focus:outline-none focus:border-brand-500/50"
                >
                  <option value="">— None —</option>
                  {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
            </div>

            <div className="px-6 pt-3 pb-6 border-t border-white/5 flex gap-2 justify-end">
              <button
                onClick={() => { setShowCashModal(false); setCashForm({ date: '', description: '', amount: '', type: 'IN', category: '' }) }}
                className="px-4 py-2 rounded-xl bg-white/[0.04] border border-white/10 text-white/50 hover:text-white/70 text-sm transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleSaveCash}
                disabled={savingCash || !cashForm.date || !cashForm.description || !cashForm.amount}
                className="px-4 py-2 rounded-xl bg-brand-600 hover:bg-brand-500 disabled:opacity-40 text-white text-sm font-medium transition-colors"
              >
                {savingCash ? (
                  <span className="flex items-center gap-2">
                    <span className="w-3.5 h-3.5 border border-white/30 border-t-white rounded-full animate-spin" />
                    Saving…
                  </span>
                ) : 'Save'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function StatementCard({ statement: s, expanded, transactions, loadingTx, processing, onToggle, onProcess, onDelete }) {
  return (
    <div className="bg-white/[0.03] border border-white/5 rounded-2xl overflow-hidden">
      {/* Card header row */}
      <div
        className={`flex items-center gap-4 px-5 py-4 ${s.processed ? 'cursor-pointer hover:bg-white/[0.02]' : ''} transition-colors`}
        onClick={onToggle}
      >
        {/* Bank icon */}
        <div className="w-9 h-9 rounded-xl bg-white/[0.04] border border-white/8 flex items-center justify-center shrink-0">
          <svg className="w-4 h-4 text-white/40" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
        </div>

        {/* Info */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-white font-medium text-sm">
              {BANK_DISPLAY[s.bankName] ?? s.bankName}
            </span>
            <span className="text-white/30 text-xs">···{s.accountLast4}</span>
            {s.accountType && (
              <span className={`text-[10px] px-2 py-0.5 rounded-full border font-medium ${TYPE_COLORS[s.accountType] ?? 'bg-white/5 text-white/30 border-white/10'}`}>
                {s.accountType}
              </span>
            )}
          </div>
          <div className="flex items-center gap-3 mt-0.5">
            <span className="text-white/40 text-xs">{periodLabel(s.month, s.year)}</span>
            {s.processed && (
              <span className="text-white/30 text-xs">{s.transactionCount} transactions</span>
            )}
          </div>
        </div>

        {/* Status + actions */}
        <div className="flex items-center gap-2 shrink-0">
          {s.processed ? (
            <span className="text-[10px] px-2.5 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-medium">
              Processed
            </span>
          ) : (
            <button
              onClick={(e) => { e.stopPropagation(); onProcess() }}
              disabled={processing}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-brand-600/20 hover:bg-brand-600/30 text-brand-400 text-xs font-medium border border-brand-500/20 transition-colors disabled:opacity-50"
            >
              {processing ? (
                <span className="w-3 h-3 border border-brand-400/40 border-t-brand-400 rounded-full animate-spin" />
              ) : (
                <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              )}
              {processing ? 'Processing…' : 'Process'}
            </button>
          )}

          <button
            onClick={(e) => { e.stopPropagation(); onDelete() }}
            className="p-1.5 rounded-lg text-white/25 hover:text-red-400 hover:bg-red-500/5 transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
          </button>

          {s.processed && (
            <svg className={`w-4 h-4 text-white/20 transition-transform duration-200 ${expanded ? 'rotate-180' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          )}
        </div>
      </div>

      {/* Transactions panel */}
      {expanded && (
        <div className="border-t border-white/5 bg-white/[0.01]">
          {loadingTx ? (
            <div className="flex justify-center py-8">
              <div className="w-5 h-5 border-2 border-brand-500 border-t-transparent rounded-full animate-spin" />
            </div>
          ) : !transactions || transactions.length === 0 ? (
            <p className="text-white/30 text-sm text-center py-8">No transactions found.</p>
          ) : (
            <div className="divide-y divide-white/[0.03]">
              {/* Table header */}
              <div className="grid grid-cols-[120px_1fr_100px_70px] gap-4 px-5 py-2 text-[11px] text-white/25 font-medium uppercase tracking-wide">
                <span>Date</span>
                <span>Description</span>
                <span className="text-right">Amount</span>
                <span className="text-center">Type</span>
              </div>
              {transactions.map((tx) => (
                <div key={tx.id} className="grid grid-cols-[120px_1fr_100px_70px] gap-4 px-5 py-2.5 items-center">
                  <span className="text-white/40 text-xs tabular-nums">
                    {new Date(tx.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                  </span>
                  <span className="text-white/70 text-xs truncate" title={tx.description}>{tx.description}</span>
                  <span className={`text-xs font-medium tabular-nums text-right ${tx.type === 'CREDIT' ? 'text-emerald-400' : 'text-white/70'}`}>
                    {tx.type === 'CREDIT' ? '+' : '-'}${Number(tx.amount).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                  </span>
                  <div className="flex justify-center">
                    <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${TX_TYPE_COLORS[tx.type] ?? ''}`}>
                      {tx.type}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

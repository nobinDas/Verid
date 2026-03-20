import { useEffect, useRef, useState } from 'react'
import { createGoal, deleteGoal, depositToGoal, getGoals, getGoalsSavingsSummary, updateGoal } from '../api/goals'
import { getSavings } from '../api/summaries'

const TERM_LABELS = { SHORT: 'Short-term', MID: 'Mid-term', LONG: 'Long-term' }
const TERM_COLORS = {
  SHORT: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
  MID:   'bg-amber-500/10 text-amber-400 border-amber-500/20',
  LONG:  'bg-purple-500/10 text-purple-400 border-purple-500/20',
}

const EMPTY_FORM = {
  title: '',
  description: '',
  targetAmount: '',
  termType: 'SHORT',
  deadline: '',
  allocationPercent: '',
}

// IQR-based outlier removal for modal allocation suggestion
function computeAvgSavings(records) {
  if (!records || records.length === 0) return 3000
  const values = records.map((r) => Number(r.netSavings)).filter((v) => v > 0)
  if (values.length === 0) return 3000
  const sorted = [...values].sort((a, b) => a - b)
  const q1 = sorted[Math.floor(sorted.length * 0.25)]
  const q3 = sorted[Math.floor(sorted.length * 0.75)]
  const iqr = q3 - q1
  const lo = q1 - 1.5 * iqr
  const hi = q3 + 1.5 * iqr
  const filtered = sorted.filter((v) => v >= lo && v <= hi)
  if (filtered.length === 0) return 3000
  return filtered.reduce((a, b) => a + b, 0) / filtered.length
}

export default function GoalsPage() {
  const [goals, setGoals] = useState([])
  const [loading, setLoading] = useState(true)
  const [avgSavings, setAvgSavings] = useState(3000)
  const [savingsSummary, setSavingsSummary] = useState({ totalSaving: 0, totalDeposited: 0, availableSaving: 0 })
  const [summaryLoaded, setSummaryLoaded] = useState(false)
  const [filterTerms, setFilterTerms] = useState(new Set())
  const [filterByDate, setFilterByDate] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [editingGoal, setEditingGoal] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [depositGoal, setDepositGoal] = useState(null) // goal being deposited to

  useEffect(() => {
    loadGoals()
    loadSavings()
  }, [])

  async function loadGoals() {
    try {
      setLoading(true)
      const data = await getGoals()
      setGoals(data)
    } catch {
      // silent
    } finally {
      setLoading(false)
    }
  }

  async function loadSavings() {
    try {
      const [records, summary] = await Promise.all([getSavings(), getGoalsSavingsSummary()])
      setAvgSavings(computeAvgSavings(records))
      setSavingsSummary({
        totalSaving:    Number(summary.totalSaving    ?? 0),
        totalDeposited: Number(summary.totalDeposited ?? 0),
        availableSaving: Number(summary.availableSaving ?? 0),
      })
      setSummaryLoaded(true)
    } catch {
      setSummaryLoaded(true)
    }
  }

  function openCreate() {
    setEditingGoal(null)
    setForm(EMPTY_FORM)
    setError('')
    setModalOpen(true)
  }

  function openEdit(goal) {
    setEditingGoal(goal)
    setForm({
      title: goal.title,
      description: goal.description || '',
      targetAmount: goal.targetAmount,
      termType: goal.termType,
      deadline: goal.deadline || '',
      allocationPercent: goal.allocationPercent,
    })
    setError('')
    setModalOpen(true)
  }

  function closeModal() {
    setModalOpen(false)
    setEditingGoal(null)
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSaving(true)
    try {
      const payload = {
        title: form.title.trim(),
        description: form.description.trim() || null,
        targetAmount: parseFloat(form.targetAmount),
        termType: form.termType,
        deadline: form.deadline || null,
        allocationPercent: form.allocationPercent !== '' ? parseFloat(form.allocationPercent) : 0,
      }
      if (editingGoal) {
        const updated = await updateGoal(editingGoal.id, payload)
        setGoals((prev) => prev.map((g) => (g.id === updated.id ? updated : g)))
      } else {
        const created = await createGoal(payload)
        setGoals((prev) => [created, ...prev])
      }
      closeModal()
    } catch (err) {
      setError(err.message || 'Something went wrong')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(goalId) {
    if (!window.confirm('Delete this goal?')) return
    try {
      await deleteGoal(goalId)
      setGoals((prev) => prev.filter((g) => g.id !== goalId))
    } catch {
      // silent
    }
  }

  async function handleDeposit(goalId, amount) {
    const updated = await depositToGoal(goalId, amount)
    setGoals((prev) => prev.map((g) => (g.id === updated.id ? updated : g)))
    // Recalculate savings summary after deposit
    setSavingsSummary((prev) => ({
      ...prev,
      totalDeposited: prev.totalDeposited + Number(amount),
      availableSaving: Math.max(0, prev.availableSaving - Number(amount)),
    }))
    setDepositGoal(null)
  }

  const totalAllocated = goals
    .filter((g) => g.status === 'ACTIVE')
    .reduce((sum, g) => sum + Number(g.allocationPercent), 0)
  const remainingPercent = Math.max(0, 100 - totalAllocated)

  const visibleGoals = goals
    .filter((g) => filterTerms.size === 0 || filterTerms.has(g.termType))
    .filter((g) => {
      if (!filterByDate) return true
      if (!g.deadline) return false
      return g.deadline <= filterByDate
    })
    .sort((a, b) => {
      if (!a.deadline && !b.deadline) return 0
      if (!a.deadline) return 1
      if (!b.deadline) return -1
      return a.deadline < b.deadline ? -1 : a.deadline > b.deadline ? 1 : 0
    })

  function toggleTerm(term) {
    setFilterTerms((prev) => {
      const next = new Set(prev)
      next.has(term) ? next.delete(term) : next.add(term)
      return next
    })
  }

  function clearFilters() {
    setFilterTerms(new Set())
    setFilterByDate('')
  }

  const hasActiveFilter = filterTerms.size > 0 || !!filterByDate

  return (
    <div className="p-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-white text-2xl font-bold">Goals</h1>
          <p className="text-white/40 text-sm mt-1">Track and manage your financial targets</p>
        </div>
        <button
          onClick={openCreate}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-brand-600 hover:bg-brand-500 text-white text-sm font-medium transition-colors duration-200"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          New Goal
        </button>
      </div>

      {/* Savings summary bar */}
      <div className="grid grid-cols-3 gap-4 mb-8">
        {/* Total Saving card */}
        <div className="bg-white/[0.03] border border-white/5 rounded-2xl p-5">
          <p className="text-white/40 text-xs mb-1">Total Saving</p>
          {summaryLoaded ? (
            <>
              <p className="text-white text-2xl font-bold">
                ${Number(savingsSummary.availableSaving).toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 0 })}
              </p>
              {savingsSummary.totalDeposited > 0 && (
                <p className="text-white/25 text-xs mt-1">
                  ${Number(savingsSummary.totalDeposited).toLocaleString(undefined, { maximumFractionDigits: 0 })} deposited to goals
                </p>
              )}
              {savingsSummary.totalSaving === 0 && (
                <p className="text-white/25 text-xs mt-1">Upload statements to calculate</p>
              )}
            </>
          ) : (
            <p className="text-white text-2xl font-bold text-white/20">—</p>
          )}
        </div>

        <div className="bg-white/[0.03] border border-white/5 rounded-2xl p-5">
          <p className="text-white/40 text-xs mb-1">Total Allocated</p>
          <p className="text-white text-2xl font-bold">{totalAllocated.toFixed(1)}%</p>
          <p className="text-white/25 text-xs mt-1">of monthly savings</p>
        </div>

        <div className="bg-white/[0.03] border border-white/5 rounded-2xl p-5">
          <p className="text-white/40 text-xs mb-1">Remaining</p>
          <p className="text-white text-2xl font-bold">{remainingPercent.toFixed(1)}%</p>
          <p className="text-white/25 text-xs mt-1">available to allocate</p>
        </div>
      </div>

      {/* Filter bar */}
      {!loading && goals.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 mb-6">
          {Object.entries(TERM_LABELS).map(([val, label]) => (
            <button
              key={val}
              onClick={() => toggleTerm(val)}
              className={`px-3 py-1.5 rounded-xl text-xs font-medium border transition-colors ${
                filterTerms.has(val)
                  ? TERM_COLORS[val]
                  : 'bg-white/[0.03] text-white/40 border-white/10 hover:text-white/60 hover:bg-white/[0.05]'
              }`}
            >
              {label}
            </button>
          ))}

          <div className="w-px h-4 bg-white/10 mx-1" />

          <div className="relative flex items-center">
            <svg className="absolute left-2.5 w-3.5 h-3.5 text-white/30 pointer-events-none" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            <input
              type="date"
              value={filterByDate}
              onChange={(e) => setFilterByDate(e.target.value)}
              className={`pl-8 pr-3 py-1.5 rounded-xl text-xs border bg-white/[0.03] focus:outline-none transition-colors [color-scheme:dark] ${
                filterByDate ? 'text-white border-brand-500/40' : 'text-white/40 border-white/10 hover:border-white/20'
              }`}
            />
          </div>

          {hasActiveFilter && (
            <button
              onClick={clearFilters}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-xl text-xs text-white/30 hover:text-white/60 border border-white/10 hover:bg-white/[0.04] transition-colors"
            >
              <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
              Clear
            </button>
          )}

          {hasActiveFilter && (
            <span className="ml-auto text-xs text-white/25">
              {visibleGoals.length} of {goals.length}
            </span>
          )}
        </div>
      )}

      {/* Goals grid */}
      {loading ? (
        <div className="flex items-center justify-center h-48">
          <div className="w-6 h-6 border-2 border-brand-500 border-t-transparent rounded-full animate-spin" />
        </div>
      ) : goals.length === 0 ? (
        <div className="bg-white/[0.03] border border-white/5 border-dashed rounded-2xl p-14 text-center">
          <div className="w-12 h-12 rounded-2xl bg-brand-950 border border-brand-800/40 flex items-center justify-center mx-auto mb-4">
            <svg className="w-6 h-6 text-brand-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
          </div>
          <h3 className="text-white font-semibold mb-2">No goals yet</h3>
          <p className="text-white/30 text-sm max-w-xs mx-auto mb-5">
            Create your first financial goal to start tracking your progress.
          </p>
          <button
            onClick={openCreate}
            className="px-4 py-2 rounded-xl bg-brand-600/20 text-brand-400 text-sm font-medium hover:bg-brand-600/30 transition-colors"
          >
            Create a goal
          </button>
        </div>
      ) : visibleGoals.length === 0 ? (
        <div className="bg-white/[0.03] border border-white/5 border-dashed rounded-2xl p-10 text-center">
          <p className="text-white/30 text-sm">No goals match the current filters.</p>
          <button onClick={clearFilters} className="mt-3 text-xs text-brand-400 hover:text-brand-300 transition-colors">
            Clear filters
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5">
          {visibleGoals.map((goal) => (
            <GoalCard
              key={goal.id}
              goal={goal}
              availableSaving={savingsSummary.availableSaving}
              onEdit={() => openEdit(goal)}
              onDelete={() => handleDelete(goal.id)}
              onDeposit={() => setDepositGoal(goal)}
            />
          ))}
        </div>
      )}

      {/* Goal create/edit modal */}
      {modalOpen && (
        <GoalModal
          form={form}
          setForm={setForm}
          onSubmit={handleSubmit}
          onClose={closeModal}
          isEditing={!!editingGoal}
          saving={saving}
          error={error}
          avgSavings={avgSavings}
        />
      )}

      {/* Deposit modal */}
      {depositGoal && (
        <DepositModal
          goal={depositGoal}
          availableSaving={savingsSummary.availableSaving}
          onClose={() => setDepositGoal(null)}
          onConfirm={handleDeposit}
        />
      )}
    </div>
  )
}

// ── GoalCard ──────────────────────────────────────────────────────────────────
function GoalCard({ goal, availableSaving, onEdit, onDelete, onDeposit }) {
  const progress    = Math.min(100, Number(goal.progressPercent))
  const isCompleted = goal.status === 'COMPLETED'
  const isCancelled = goal.status === 'CANCELLED'
  const isActive    = goal.status === 'ACTIVE'

  const showDeposit = isActive && !goal.depositedThisMonth && availableSaving > 0

  return (
    <div className="bg-white/[0.03] border border-white/5 rounded-2xl p-6 flex flex-col gap-4">
      {/* Top row */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <h3 className="text-white font-semibold truncate">{goal.title}</h3>
          {goal.description && (
            <p className="text-white/35 text-xs mt-0.5 line-clamp-2">{goal.description}</p>
          )}
        </div>
        <span className={`shrink-0 text-xs px-2.5 py-1 rounded-full border font-medium ${TERM_COLORS[goal.termType]}`}>
          {TERM_LABELS[goal.termType]}
        </span>
      </div>

      {/* Progress */}
      <div>
        <div className="flex justify-between text-xs mb-1.5">
          <span className="text-white/40">Progress</span>
          <span className="text-white/60">{progress.toFixed(1)}%</span>
        </div>
        <div className="h-1.5 rounded-full bg-white/5 overflow-hidden">
          <div
            className={`h-full rounded-full transition-all duration-500 ${isCompleted ? 'bg-emerald-500' : 'bg-brand-500'}`}
            style={{ width: `${progress}%` }}
          />
        </div>
        <div className="flex justify-between text-xs mt-1.5">
          <span className="text-white/40">
            {Number(goal.currentAmount) === 0 ? '—' : `$${Number(goal.currentAmount).toLocaleString()}`}
          </span>
          <span className="text-white/60">${Number(goal.targetAmount).toLocaleString()}</span>
        </div>
      </div>

      {/* Meta row */}
      <div className="flex items-center gap-3 text-xs text-white/35">
        <span className="flex items-center gap-1">
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
          {Number(goal.allocationPercent)}% allocated
        </span>
        {goal.deadline && (
          <span className="flex items-center gap-1">
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            {new Date(goal.deadline).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
          </span>
        )}
        {(isCompleted || isCancelled) && (
          <span className={`ml-auto px-2 py-0.5 rounded-full border text-[10px] font-semibold uppercase tracking-wide ${
            isCompleted ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-white/5 text-white/30 border-white/10'
          }`}>
            {goal.status.toLowerCase()}
          </span>
        )}
        {isActive && goal.depositedThisMonth && (
          <span className="ml-auto flex items-center gap-1 text-emerald-400/70 text-[10px]">
            <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" />
            </svg>
            Deposited this month
          </span>
        )}
      </div>

      {/* Deposit CTA */}
      {showDeposit && (
        <button
          onClick={onDeposit}
          className="flex items-center justify-center gap-2 w-full py-2 rounded-xl bg-brand-600/15 hover:bg-brand-600/25 text-brand-400 hover:text-brand-300 text-xs font-medium border border-brand-500/20 hover:border-brand-500/35 transition-colors"
        >
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Deposit for this month
        </button>
      )}

      {/* Actions */}
      <div className="flex gap-2 pt-1 border-t border-white/5">
        <button
          onClick={onEdit}
          className="flex-1 text-xs text-white/40 hover:text-white/70 py-1.5 rounded-lg hover:bg-white/[0.04] transition-colors"
        >
          Edit
        </button>
        <button
          onClick={onDelete}
          className="flex-1 text-xs text-white/40 hover:text-red-400 py-1.5 rounded-lg hover:bg-red-500/5 transition-colors"
        >
          Delete
        </button>
      </div>
    </div>
  )
}

// ── DepositModal ──────────────────────────────────────────────────────────────
function DepositModal({ goal, availableSaving, onClose, onConfirm }) {
  const allocAmount = Number(goal.allocationPercent) > 0
    ? (Number(goal.allocationPercent) / 100) * availableSaving
    : null

  const [mode, setMode]       = useState(allocAmount !== null ? 'alloc' : 'custom')
  const [custom, setCustom]   = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError]     = useState('')

  const depositAmount = mode === 'alloc'
    ? allocAmount
    : parseFloat(custom) || 0

  const isValid = depositAmount > 0 && depositAmount <= availableSaving

  async function handleSubmit(e) {
    e.preventDefault()
    if (!isValid) return
    setError('')
    setSubmitting(true)
    try {
      await onConfirm(goal.id, depositAmount)
    } catch (err) {
      setError(err.message || 'Deposit failed')
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div className="w-full max-w-sm bg-[#111827] border border-white/10 rounded-2xl shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-white/5">
          <div>
            <h2 className="text-white font-semibold">Deposit to Goal</h2>
            <p className="text-white/40 text-xs mt-0.5 truncate max-w-[200px]">{goal.title}</p>
          </div>
          <button onClick={onClose} className="text-white/40 hover:text-white/70 transition-colors">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-5">
          {/* Available savings info */}
          <div className="flex items-center justify-between bg-white/[0.03] border border-white/5 rounded-xl px-4 py-3">
            <span className="text-white/40 text-xs">Available savings</span>
            <span className="text-white font-semibold text-sm">
              ${availableSaving.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </span>
          </div>

          {error && (
            <p className="text-red-400 text-sm bg-red-500/10 border border-red-500/20 rounded-xl px-3 py-2">
              {error}
            </p>
          )}

          {/* Mode selector */}
          <div className="space-y-2">
            {allocAmount !== null && (
              <button
                type="button"
                onClick={() => setMode('alloc')}
                className={`w-full flex items-center justify-between px-4 py-3.5 rounded-xl border text-left transition-colors ${
                  mode === 'alloc'
                    ? 'bg-brand-600/15 border-brand-500/30 text-white'
                    : 'bg-white/[0.03] border-white/10 text-white/50 hover:text-white/70 hover:border-white/20'
                }`}
              >
                <div>
                  <p className="text-sm font-medium">Use allocation %</p>
                  <p className="text-xs mt-0.5 opacity-60">
                    {Number(goal.allocationPercent)}% of your savings
                  </p>
                </div>
                <span className={`text-base font-bold ${mode === 'alloc' ? 'text-brand-400' : 'text-white/30'}`}>
                  ${allocAmount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                </span>
              </button>
            )}

            <button
              type="button"
              onClick={() => setMode('custom')}
              className={`w-full flex items-center justify-between px-4 py-3.5 rounded-xl border text-left transition-colors ${
                mode === 'custom'
                  ? 'bg-brand-600/15 border-brand-500/30 text-white'
                  : 'bg-white/[0.03] border-white/10 text-white/50 hover:text-white/70 hover:border-white/20'
              }`}
            >
              <div>
                <p className="text-sm font-medium">Custom amount</p>
                <p className="text-xs mt-0.5 opacity-60">Enter any amount</p>
              </div>
              {mode === 'custom' && (
                <svg className="w-4 h-4 text-brand-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                </svg>
              )}
            </button>
          </div>

          {/* Custom amount input */}
          {mode === 'custom' && (
            <div>
              <label className="block text-white/50 text-xs mb-1.5">Amount</label>
              <input
                type="number"
                min="0.01"
                step="0.01"
                max={availableSaving}
                placeholder="0.00"
                autoFocus
                value={custom}
                onChange={(e) => setCustom(e.target.value)}
                className={`w-full bg-white/[0.04] border rounded-xl px-3 py-2.5 text-white text-sm placeholder-white/20 focus:outline-none transition-colors ${
                  custom && parseFloat(custom) > availableSaving
                    ? 'border-red-500/50 focus:border-red-500/70'
                    : 'border-white/10 focus:border-brand-500/50'
                }`}
              />
              {custom && parseFloat(custom) > availableSaving && (
                <p className="text-red-400 text-xs mt-1.5">
                  Exceeds available savings of ${availableSaving.toLocaleString(undefined, { maximumFractionDigits: 2 })}
                </p>
              )}
            </div>
          )}

          {/* Summary line */}
          {depositAmount > 0 && isValid && (
            <div className="flex items-center justify-between text-xs text-white/40 border-t border-white/5 pt-3">
              <span>Depositing</span>
              <span className="text-white font-semibold">
                ${depositAmount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </span>
            </div>
          )}

          <div className="flex gap-3">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-2.5 rounded-xl border border-white/10 text-white/40 text-sm font-medium hover:text-white/60 hover:bg-white/[0.03] transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={!isValid || submitting}
              className="flex-1 py-2.5 rounded-xl bg-brand-600 hover:bg-brand-500 disabled:opacity-40 text-white text-sm font-medium transition-colors"
            >
              {submitting ? 'Depositing…' : 'Confirm Deposit'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ── helpers ───────────────────────────────────────────────────────────────────
function fmtCurrency(n) {
  if (n >= 10000) return `$${(n / 1000).toFixed(1)}k`
  if (n >= 1000)  return `$${(n / 1000).toFixed(2)}k`
  return `$${n.toFixed(0)}`
}

function computeAllocationSuggestion(targetAmount, deadline) {
  const target = parseFloat(targetAmount)
  if (!targetAmount || !deadline || isNaN(target) || target <= 0) return null
  const today = new Date(); today.setHours(0, 0, 0, 0)
  const end = new Date(deadline)
  if (end <= today) return null
  const diffDays = (end - today) / 86400000
  const months = diffDays / 30.44
  const weeks  = diffDays / 7
  return {
    monthlyNeeded: target / months,
    weeklyNeeded:  target / weeks,
    months: Math.round(months),
    weeks:  Math.round(weeks),
  }
}

function AllocationTip({ suggestion }) {
  return (
    <div className="absolute left-full top-1/2 -translate-y-1/2 ml-2 z-10 w-64 bg-[#1a2235] border border-white/10 rounded-xl shadow-2xl p-3.5 text-xs">
      <div className="absolute right-full top-1/2 -translate-y-1/2 border-4 border-transparent border-r-[#1a2235]" />
      {suggestion ? (
        <>
          <p className="text-brand-400 font-semibold mb-2 flex items-center gap-1.5">
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
            Savings needed to hit your goal
          </p>
          <div className="space-y-1.5 mb-2.5">
            <div className="flex justify-between">
              <span className="text-white/40">Monthly</span>
              <span className="text-white font-semibold">{fmtCurrency(suggestion.monthlyNeeded)}/mo</span>
            </div>
            <div className="flex justify-between">
              <span className="text-white/40">Weekly</span>
              <span className="text-white font-semibold">{fmtCurrency(suggestion.weeklyNeeded)}/wk</span>
            </div>
            <div className="flex justify-between">
              <span className="text-white/40">Timeline</span>
              <span className="text-white/70">{suggestion.months} months</span>
            </div>
          </div>
          <p className="text-white/30 leading-relaxed border-t border-white/5 pt-2">
            Set your allocation % so that percentage of your monthly savings covers{' '}
            <span className="text-white/50">{fmtCurrency(suggestion.monthlyNeeded)}/mo</span>.
          </p>
        </>
      ) : (
        <>
          <p className="text-white/70 font-medium mb-1.5">What is Allocation %?</p>
          <p className="text-white/40 leading-relaxed">
            The percentage of your <span className="text-white/60">net monthly savings</span> that
            automatically flows toward this goal each month.
          </p>
          <p className="text-white/30 mt-2 leading-relaxed">
            Fill in a target amount and deadline to see how much you need to save monthly.
          </p>
        </>
      )}
    </div>
  )
}

function GoalModal({ form, setForm, onSubmit, onClose, isEditing, saving, error, avgSavings }) {
  const [tipOpen, setTipOpen] = useState(false)
  const tipRef = useRef(null)

  const titleRef    = useRef(null)
  const descRef     = useRef(null)
  const targetRef   = useRef(null)
  const allocRef    = useRef(null)
  const deadlineRef = useRef(null)
  const saveRef     = useRef(null)
  const navOrder    = [titleRef, descRef, targetRef, allocRef, deadlineRef, saveRef]

  function navNext(currentRef) {
    const idx = navOrder.findIndex((r) => r === currentRef)
    const next = navOrder[idx + 1]
    if (next?.current) next.current.focus()
  }

  function onEnter(e, currentRef) {
    if (e.key !== 'Enter') return
    e.preventDefault()
    navNext(currentRef)
  }

  useEffect(() => {
    if (!tipOpen) return
    function handle(e) {
      if (tipRef.current && !tipRef.current.contains(e.target)) setTipOpen(false)
    }
    document.addEventListener('mousedown', handle)
    return () => document.removeEventListener('mousedown', handle)
  }, [tipOpen])

  function field(key) {
    return {
      value: form[key],
      onChange: (e) => setForm((prev) => ({ ...prev, [key]: e.target.value })),
    }
  }

  const suggestion  = computeAllocationSuggestion(form.targetAmount, form.deadline)
  const hasAlloc    = form.allocationPercent !== '' && parseFloat(form.allocationPercent) > 0
  const suggestedPct =
    suggestion && avgSavings > 0
      ? Math.ceil((suggestion.monthlyNeeded / avgSavings) * 1000) / 10
      : null
  const impliedSavings =
    suggestion && hasAlloc
      ? suggestion.monthlyNeeded / (parseFloat(form.allocationPercent) / 100)
      : null
  const mismatchPct =
    suggestedPct !== null && hasAlloc ? suggestedPct.toFixed(1) : null
  const isMismatch =
    mismatchPct !== null &&
    Math.abs(parseFloat(mismatchPct) - parseFloat(form.allocationPercent)) > 2

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div className="w-full max-w-md bg-[#111827] border border-white/10 rounded-2xl shadow-2xl">
        <div className="flex items-center justify-between px-6 py-4 border-b border-white/5">
          <h2 className="text-white font-semibold">{isEditing ? 'Edit Goal' : 'New Goal'}</h2>
          <button onClick={onClose} className="text-white/40 hover:text-white/70 transition-colors">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <form onSubmit={onSubmit} className="px-6 py-5 space-y-4">
          {error && (
            <p className="text-red-400 text-sm bg-red-500/10 border border-red-500/20 rounded-xl px-3 py-2">
              {error}
            </p>
          )}

          <div>
            <label className="block text-white/50 text-xs mb-1.5">Title *</label>
            <input
              ref={titleRef}
              required
              placeholder="e.g. Emergency Fund"
              className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2.5 text-white text-sm placeholder-white/20 focus:outline-none focus:border-brand-500/50 transition-colors"
              onKeyDown={(e) => onEnter(e, titleRef)}
              {...field('title')}
            />
          </div>

          <div>
            <label className="block text-white/50 text-xs mb-1.5">Description</label>
            <textarea
              ref={descRef}
              rows={2}
              placeholder="Optional notes about this goal"
              className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2.5 text-white text-sm placeholder-white/20 focus:outline-none focus:border-brand-500/50 transition-colors resize-none"
              onKeyDown={(e) => onEnter(e, descRef)}
              {...field('description')}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-white/50 text-xs mb-1.5">Target Amount *</label>
              <input
                ref={targetRef}
                required
                type="number"
                min="0.01"
                step="0.01"
                placeholder="0.00"
                className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2.5 text-white text-sm placeholder-white/20 focus:outline-none focus:border-brand-500/50 transition-colors"
                onKeyDown={(e) => onEnter(e, targetRef)}
                {...field('targetAmount')}
              />
            </div>

            <div>
              <div className="flex items-center gap-1.5 mb-1.5">
                <label className="text-white/50 text-xs">Allocation %</label>
                <div className="relative" ref={tipRef}>
                  <button
                    type="button"
                    onClick={() => setTipOpen((o) => !o)}
                    className={`w-4 h-4 rounded-full flex items-center justify-center transition-colors ${
                      tipOpen
                        ? 'bg-brand-500/30 text-brand-300'
                        : suggestion
                        ? 'bg-brand-500/15 text-brand-400 hover:bg-brand-500/25'
                        : 'bg-white/10 text-white/30 hover:bg-white/15 hover:text-white/50'
                    }`}
                  >
                    {suggestion ? (
                      <svg className="w-2.5 h-2.5" fill="currentColor" viewBox="0 0 24 24">
                        <path d="M12 2l2.4 7.4H22l-6.2 4.5 2.4 7.4L12 17l-6.2 4.3 2.4-7.4L2 9.4h7.6z" />
                      </svg>
                    ) : (
                      <svg className="w-2.5 h-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                      </svg>
                    )}
                  </button>
                  {tipOpen && <AllocationTip suggestion={suggestion} />}
                </div>
              </div>

              <input
                ref={allocRef}
                type="number"
                min="0"
                max="100"
                step="0.1"
                placeholder={suggestedPct !== null ? String(suggestedPct) : '0'}
                className={`w-full bg-white/[0.04] border rounded-xl px-3 py-2.5 text-white text-sm focus:outline-none transition-colors ${
                  isMismatch
                    ? 'border-amber-500/40 focus:border-amber-500/60 placeholder-amber-400/25'
                    : suggestedPct !== null && !hasAlloc
                    ? 'border-brand-500/30 focus:border-brand-500/50 placeholder-brand-400/40'
                    : 'border-white/10 focus:border-brand-500/50 placeholder-white/20'
                }`}
                onKeyDown={(e) => onEnter(e, allocRef)}
                {...field('allocationPercent')}
              />

              {suggestion && hasAlloc && impliedSavings && (
                <div className="mt-1.5 space-y-1">
                  <p className="text-[11px] text-white/30 flex items-center gap-1">
                    <svg className="w-3 h-3 text-white/20 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                    </svg>
                    At <span className="text-white/50 font-medium">{form.allocationPercent}%</span>, you need{' '}
                    <span className="text-white/50 font-medium">~{fmtCurrency(impliedSavings)}/mo</span> total savings
                  </p>
                  {isMismatch && (
                    <div className="flex items-start gap-1.5 bg-amber-500/8 border border-amber-500/20 rounded-lg px-2.5 py-2">
                      <svg className="w-3 h-3 text-amber-400 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
                      </svg>
                      <p className="text-[11px] text-amber-400/80 leading-relaxed">
                        Consider{' '}
                        <button
                          type="button"
                          onClick={() => setForm((prev) => ({ ...prev, allocationPercent: mismatchPct }))}
                          className="text-amber-300 font-semibold underline underline-offset-2 hover:text-amber-200"
                        >
                          {mismatchPct}% instead
                        </button>{' '}
                        — it aligns with{' '}
                        <span className="text-amber-300/80">{fmtCurrency(suggestion.monthlyNeeded)}/mo</span>{' '}
                        at your current savings rate. Up to you.
                      </p>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>

          <div>
            <label className="block text-white/50 text-xs mb-1.5">Term *</label>
            <div className="grid grid-cols-3 gap-2">
              {Object.entries(TERM_LABELS).map(([val, label]) => (
                <button
                  key={val}
                  type="button"
                  onClick={() => setForm((prev) => ({ ...prev, termType: val }))}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault()
                      setForm((prev) => ({ ...prev, termType: val }))
                      deadlineRef.current?.focus()
                    }
                  }}
                  className={`py-2 rounded-xl text-xs font-medium border transition-colors ${
                    form.termType === val
                      ? 'bg-brand-600/20 text-brand-400 border-brand-500/30'
                      : 'bg-white/[0.03] text-white/40 border-white/10 hover:text-white/60'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-white/50 text-xs mb-1.5">Deadline</label>
            <input
              ref={deadlineRef}
              type="date"
              className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2.5 text-white text-sm focus:outline-none focus:border-brand-500/50 transition-colors [color-scheme:dark]"
              onKeyDown={(e) => onEnter(e, deadlineRef)}
              {...field('deadline')}
            />
          </div>

          <div className="flex gap-3 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-2.5 rounded-xl border border-white/10 text-white/40 text-sm font-medium hover:text-white/60 hover:bg-white/[0.03] transition-colors"
            >
              Cancel
            </button>
            <button
              ref={saveRef}
              type="submit"
              disabled={saving}
              className="flex-1 py-2.5 rounded-xl bg-brand-600 hover:bg-brand-500 disabled:opacity-50 text-white text-sm font-medium transition-colors"
            >
              {saving ? 'Saving…' : isEditing ? 'Save Changes' : 'Create Goal'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

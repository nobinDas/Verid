import { useEffect, useState } from 'react'
import { getInvestments, createInvestment, updateInvestment, deleteInvestment } from '../api/investments'

const emptyForm = {
  platform: '',
  amount: '',
  investedAt: '',
  recipient: '',
  contactInfo: '',
  expectedReturnDate: '',
  interestRate: '',
  notes: '',
}

export default function InvestmentsPage() {
  const [investments, setInvestments] = useState([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => { load() }, [])

  async function load() {
    setLoading(true)
    try {
      const data = await getInvestments()
      setInvestments(data)
    } catch { setError('Failed to load investments') }
    finally { setLoading(false) }
  }

  const platform = investments.filter(i => i.source === 'STATEMENT')
  const manual   = investments.filter(i => i.source === 'MANUAL')

  function openAdd() {
    setForm(emptyForm)
    setEditingId(null)
    setError('')
    setModalOpen(true)
  }

  function openEdit(inv) {
    setForm({
      platform:           inv.platform || '',
      amount:             inv.amount || '',
      investedAt:         inv.investedAt || '',
      recipient:          inv.recipient || '',
      contactInfo:        inv.contactInfo || '',
      expectedReturnDate: inv.expectedReturnDate || '',
      interestRate:       inv.interestRate || '',
      notes:              inv.notes || '',
    })
    setEditingId(inv.id)
    setError('')
    setModalOpen(true)
  }

  function closeModal() {
    setModalOpen(false)
    setEditingId(null)
  }

  function handleChange(e) {
    setForm(f => ({ ...f, [e.target.name]: e.target.value }))
  }

  function handleKey(e) {
    if (e.key === 'Enter') {
      const fields = ['platform','amount','investedAt','recipient','contactInfo','expectedReturnDate','interestRate','notes']
      const idx = fields.indexOf(e.target.name)
      if (idx < fields.length - 1) {
        document.querySelector(`[name=${fields[idx + 1]}]`)?.focus()
        e.preventDefault()
      }
    }
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setSaving(true)
    setError('')
    try {
      const payload = {
        platform:           form.platform,
        amount:             parseFloat(form.amount),
        investedAt:         form.investedAt,
        recipient:          form.recipient || null,
        contactInfo:        form.contactInfo || null,
        expectedReturnDate: form.expectedReturnDate || null,
        interestRate:       form.interestRate ? parseFloat(form.interestRate) : null,
        notes:              form.notes || null,
      }
      if (editingId) {
        await updateInvestment(editingId, payload)
      } else {
        await createInvestment(payload)
      }
      closeModal()
      await load()
    } catch (err) {
      setError(err.message || 'Failed to save')
    } finally { setSaving(false) }
  }

  async function handleDelete(id) {
    if (!confirm('Delete this investment?')) return
    try {
      await deleteInvestment(id)
      await load()
    } catch { setError('Failed to delete') }
  }

  function fmt(n) {
    return n != null ? `$${Number(n).toLocaleString('en-US', { minimumFractionDigits: 2 })}` : '—'
  }

  function fmtDate(d) {
    if (!d) return '—'
    return new Date(d + 'T00:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
  }

  function daysLabel(days) {
    if (days == null) return <span className="text-white/30">—</span>
    if (days < 0) return <span className="text-red-400 font-medium">{Math.abs(days)}d overdue</span>
    if (days === 0) return <span className="text-amber-400 font-medium">Due today</span>
    return <span className="text-emerald-400 font-medium">{days}d left</span>
  }

  const totalInvested = manual.reduce((sum, i) => sum + Number(i.amount), 0)
  const totalExpected = manual.reduce((sum, i) => sum + (i.expectedReturn ? Number(i.expectedReturn) : Number(i.amount)), 0)
  const platformTotal = platform.reduce((sum, i) => sum + Number(i.amount), 0)

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <div className="w-6 h-6 border-2 border-brand-500 border-t-transparent rounded-full animate-spin" />
    </div>
  )

  return (
    <div className="p-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-white text-2xl font-bold">Investments</h1>
          <p className="text-white/40 text-sm mt-1">Track your brokerage, crypto, and personal investments</p>
        </div>
        <button
          onClick={openAdd}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-brand-600 hover:bg-brand-500 text-white text-sm font-medium transition-colors duration-200"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Add Investment
        </button>
      </div>

      {error && (
        <div className="mb-6 text-red-400 text-sm bg-red-500/10 border border-red-500/20 rounded-xl px-4 py-3">
          {error}
        </div>
      )}

      {/* Summary cards */}
      <div className="grid grid-cols-3 gap-4 mb-8">
        <div className="bg-white/[0.03] border border-white/5 rounded-2xl p-5">
          <p className="text-white/40 text-xs mb-1">Platform Total</p>
          <p className="text-white text-2xl font-bold">{platformTotal > 0 ? fmt(platformTotal) : '—'}</p>
          <p className="text-white/25 text-xs mt-1">Fidelity &amp; Coinbase</p>
        </div>
        <div className="bg-white/[0.03] border border-white/5 rounded-2xl p-5">
          <p className="text-white/40 text-xs mb-1">Personal Invested</p>
          <p className="text-white text-2xl font-bold">{totalInvested > 0 ? fmt(totalInvested) : '—'}</p>
          <p className="text-white/25 text-xs mt-1">{manual.length} investment{manual.length !== 1 ? 's' : ''}</p>
        </div>
        <div className="bg-white/[0.03] border border-white/5 rounded-2xl p-5">
          <p className="text-white/40 text-xs mb-1">Expected Returns</p>
          <p className="text-white text-2xl font-bold">{totalExpected > 0 ? fmt(totalExpected) : '—'}</p>
          <p className="text-white/25 text-xs mt-1">with interest</p>
        </div>
      </div>

      {/* Platform Investments */}
      <section className="mb-10">
        <div className="mb-4">
          <h2 className="text-white font-semibold text-lg">Brokerage &amp; Crypto</h2>
          <p className="text-white/40 text-sm mt-0.5">Auto-detected from processed statements (Fidelity, Coinbase).</p>
        </div>

        {platform.length === 0 ? (
          <div className="bg-white/[0.03] border border-white/5 border-dashed rounded-2xl p-10 text-center">
            <p className="text-white/30 text-sm">No platform investments detected yet.</p>
            <p className="text-white/20 text-xs mt-1">Process a statement containing Fidelity or Coinbase transactions.</p>
          </div>
        ) : (
          <div className="bg-white/[0.03] border border-white/5 rounded-2xl overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-white/5">
                  <th className="px-5 py-3.5 text-left text-xs text-white/40 font-medium uppercase tracking-wide">Platform</th>
                  <th className="px-5 py-3.5 text-left text-xs text-white/40 font-medium uppercase tracking-wide">Date</th>
                  <th className="px-5 py-3.5 text-right text-xs text-white/40 font-medium uppercase tracking-wide">Amount</th>
                  <th className="px-5 py-3.5 text-left text-xs text-white/40 font-medium uppercase tracking-wide">Description</th>
                </tr>
              </thead>
              <tbody>
                {platform.map((inv, idx) => (
                  <tr key={inv.id} className={`${idx !== platform.length - 1 ? 'border-b border-white/[0.04]' : ''} hover:bg-white/[0.02] transition-colors`}>
                    <td className="px-5 py-3.5">
                      <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${
                        inv.platform === 'Fidelity' ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' :
                        inv.platform === 'Coinbase' ? 'bg-blue-500/10 text-blue-400 border border-blue-500/20' :
                        'bg-white/5 text-white/50 border border-white/10'
                      }`}>{inv.platform}</span>
                    </td>
                    <td className="px-5 py-3.5 text-white/50 text-xs">{fmtDate(inv.investedAt)}</td>
                    <td className="px-5 py-3.5 text-right text-white font-medium">{fmt(inv.amount)}</td>
                    <td className="px-5 py-3.5 text-white/40 text-xs max-w-xs truncate">{inv.notes || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Personal Investments */}
      <section>
        <div className="mb-4">
          <h2 className="text-white font-semibold text-lg">Personal Investments</h2>
          <p className="text-white/40 text-sm mt-0.5">Track money lent or invested with individuals or organizations.</p>
        </div>

        {manual.length === 0 ? (
          <div className="bg-white/[0.03] border border-white/5 border-dashed rounded-2xl p-14 text-center">
            <div className="w-12 h-12 rounded-2xl bg-brand-950 border border-brand-800/40 flex items-center justify-center mx-auto mb-4">
              <svg className="w-6 h-6 text-brand-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <h3 className="text-white font-semibold mb-2">No personal investments yet</h3>
            <p className="text-white/30 text-sm max-w-xs mx-auto mb-5">
              Record money you've lent or invested with individuals or organizations.
            </p>
            <button
              onClick={openAdd}
              className="px-4 py-2 rounded-xl bg-brand-600/20 text-brand-400 text-sm font-medium hover:bg-brand-600/30 transition-colors"
            >
              Add your first investment
            </button>
          </div>
        ) : (
          <div className="bg-white/[0.03] border border-white/5 rounded-2xl overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-white/5">
                  <th className="px-5 py-3.5 text-left text-xs text-white/40 font-medium uppercase tracking-wide">Name</th>
                  <th className="px-5 py-3.5 text-left text-xs text-white/40 font-medium uppercase tracking-wide">Recipient</th>
                  <th className="px-5 py-3.5 text-left text-xs text-white/40 font-medium uppercase tracking-wide">Date</th>
                  <th className="px-5 py-3.5 text-right text-xs text-white/40 font-medium uppercase tracking-wide">Amount</th>
                  <th className="px-5 py-3.5 text-right text-xs text-white/40 font-medium uppercase tracking-wide">Expected Return</th>
                  <th className="px-5 py-3.5 text-center text-xs text-white/40 font-medium uppercase tracking-wide">Due</th>
                  <th className="px-5 py-3.5 text-left text-xs text-white/40 font-medium uppercase tracking-wide">Notes</th>
                  <th className="px-5 py-3.5" />
                </tr>
              </thead>
              <tbody>
                {manual.map((inv, idx) => (
                  <tr key={inv.id} className={`${idx !== manual.length - 1 ? 'border-b border-white/[0.04]' : ''} hover:bg-white/[0.02] transition-colors`}>
                    <td className="px-5 py-3.5 text-white font-medium">{inv.platform}</td>
                    <td className="px-5 py-3.5 text-white/50 text-xs">{inv.recipient || '—'}</td>
                    <td className="px-5 py-3.5 text-white/50 text-xs">{fmtDate(inv.investedAt)}</td>
                    <td className="px-5 py-3.5 text-right text-white font-medium">{fmt(inv.amount)}</td>
                    <td className="px-5 py-3.5 text-right">
                      <span className="text-brand-400 font-medium">{inv.expectedReturn ? fmt(inv.expectedReturn) : '—'}</span>
                      {inv.interestRate ? <span className="text-white/30 text-xs ml-1">({inv.interestRate}%)</span> : null}
                    </td>
                    <td className="px-5 py-3.5 text-center text-xs">{daysLabel(inv.daysUntilReturn)}</td>
                    <td className="px-5 py-3.5 text-white/40 text-xs max-w-[140px] truncate">{inv.notes || '—'}</td>
                    <td className="px-5 py-3.5">
                      <div className="flex gap-3 justify-end">
                        <button onClick={() => openEdit(inv)} className="text-xs text-white/40 hover:text-brand-400 transition-colors">Edit</button>
                        <button onClick={() => handleDelete(inv.id)} className="text-xs text-white/40 hover:text-red-400 transition-colors">Delete</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Add / Edit Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="w-full max-w-lg bg-[#111827] border border-white/10 rounded-2xl shadow-2xl">
            <div className="flex items-center justify-between px-6 py-4 border-b border-white/5">
              <h2 className="text-white font-semibold">{editingId ? 'Edit Investment' : 'New Investment'}</h2>
              <button onClick={closeModal} className="text-white/40 hover:text-white/70 transition-colors">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
              {error && (
                <p className="text-red-400 text-sm bg-red-500/10 border border-red-500/20 rounded-xl px-3 py-2">{error}</p>
              )}

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-white/50 text-xs mb-1.5">Platform / Name *</label>
                  <input
                    name="platform" value={form.platform} onChange={handleChange} onKeyDown={handleKey}
                    required placeholder="e.g. Fidelity, John Doe"
                    className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2.5 text-white text-sm placeholder-white/20 focus:outline-none focus:border-brand-500/50 transition-colors"
                  />
                </div>
                <div>
                  <label className="block text-white/50 text-xs mb-1.5">Amount ($) *</label>
                  <input
                    name="amount" value={form.amount} onChange={handleChange} onKeyDown={handleKey}
                    required type="number" step="0.01" min="0.01" placeholder="0.00"
                    className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2.5 text-white text-sm placeholder-white/20 focus:outline-none focus:border-brand-500/50 transition-colors"
                  />
                </div>
                <div>
                  <label className="block text-white/50 text-xs mb-1.5">Date Invested *</label>
                  <input
                    name="investedAt" value={form.investedAt} onChange={handleChange} onKeyDown={handleKey}
                    required type="date"
                    className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2.5 text-white text-sm focus:outline-none focus:border-brand-500/50 transition-colors [color-scheme:dark]"
                  />
                </div>
                <div>
                  <label className="block text-white/50 text-xs mb-1.5">Recipient</label>
                  <input
                    name="recipient" value={form.recipient} onChange={handleChange} onKeyDown={handleKey}
                    placeholder="Person or organization"
                    className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2.5 text-white text-sm placeholder-white/20 focus:outline-none focus:border-brand-500/50 transition-colors"
                  />
                </div>
                <div>
                  <label className="block text-white/50 text-xs mb-1.5">Contact Info</label>
                  <input
                    name="contactInfo" value={form.contactInfo} onChange={handleChange} onKeyDown={handleKey}
                    placeholder="Phone, email, etc."
                    className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2.5 text-white text-sm placeholder-white/20 focus:outline-none focus:border-brand-500/50 transition-colors"
                  />
                </div>
                <div>
                  <label className="block text-white/50 text-xs mb-1.5">Expected Return Date</label>
                  <input
                    name="expectedReturnDate" value={form.expectedReturnDate} onChange={handleChange} onKeyDown={handleKey}
                    type="date"
                    className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2.5 text-white text-sm focus:outline-none focus:border-brand-500/50 transition-colors [color-scheme:dark]"
                  />
                </div>
                <div>
                  <label className="block text-white/50 text-xs mb-1.5">Interest Rate (%)</label>
                  <input
                    name="interestRate" value={form.interestRate} onChange={handleChange} onKeyDown={handleKey}
                    type="number" step="0.01" min="0" placeholder="0.00"
                    className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2.5 text-white text-sm placeholder-white/20 focus:outline-none focus:border-brand-500/50 transition-colors"
                  />
                </div>
                <div>
                  <label className="block text-white/50 text-xs mb-1.5">Notes</label>
                  <input
                    name="notes" value={form.notes} onChange={handleChange} onKeyDown={handleKey}
                    placeholder="Any additional notes"
                    className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2.5 text-white text-sm placeholder-white/20 focus:outline-none focus:border-brand-500/50 transition-colors"
                  />
                </div>
              </div>

              <div className="flex gap-3 pt-1">
                <button
                  type="button" onClick={closeModal}
                  className="flex-1 py-2.5 rounded-xl border border-white/10 text-white/40 text-sm font-medium hover:text-white/60 hover:bg-white/[0.03] transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit" disabled={saving}
                  className="flex-1 py-2.5 rounded-xl bg-brand-600 hover:bg-brand-500 disabled:opacity-50 text-white text-sm font-medium transition-colors"
                >
                  {saving ? 'Saving…' : editingId ? 'Save Changes' : 'Add Investment'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

import { useEffect, useState, useMemo } from 'react'
import {
  PieChart, Pie, Cell,
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  LineChart, Line,
} from 'recharts'
import { getSavings } from '../api/summaries'
import { getCategories, getTopTransactions } from '../api/analytics'

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

const SLICE_COLORS = [
  '#818cf8', '#34d399', '#f87171', '#fbbf24',
  '#60a5fa', '#a78bfa', '#fb923c', '#2dd4bf', '#f472b6', '#94a3b8',
]

function fmt(n) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(n)
}

function fmtK(v) {
  return `$${Math.abs(v) >= 1000 ? (v / 1000).toFixed(0) + 'k' : v}`
}

function toYearMonth(s) {
  return `${s.year}-${String(s.month).padStart(2, '0')}`
}

const EXPENSE_CLS = new Set(['EXPENSE', 'CASH_OUT', 'CC_CHARGE'])
const INCOME_CLS  = new Set(['INCOME', 'EXTRA_IN', 'CASH_IN'])

export default function AnalyticsPage() {
  const [summaries, setSummaries]   = useState([])
  const [categories, setCategories] = useState([])
  const [topTx, setTopTx]           = useState([])
  const [loading, setLoading]       = useState(true)
  const [activeTab, setActiveTab]   = useState('expenses')
  const [fromMonth, setFromMonth]   = useState('')
  const [toMonth, setToMonth]       = useState('')

  // Fetch summaries once to populate month picker
  useEffect(() => {
    getSavings()
      .then(s => {
        setSummaries(s)
        if (s.length > 0) {
          const sorted = [...s].sort((a, b) => (a.year * 12 + a.month) - (b.year * 12 + b.month))
          const toIdx   = sorted.length - 1
          const fromIdx = Math.max(0, toIdx - 5) // default last 6 months
          setToMonth(toYearMonth(sorted[toIdx]))
          setFromMonth(toYearMonth(sorted[fromIdx]))
        }
      })
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  // Fetch analytics data whenever month range changes
  useEffect(() => {
    if (!fromMonth || !toMonth) return
    Promise.all([
      getCategories(fromMonth, toMonth),
      getTopTransactions(fromMonth, toMonth),
    ])
      .then(([cats, txs]) => { setCategories(cats); setTopTx(txs) })
      .catch(console.error)
  }, [fromMonth, toMonth])

  // Available months for the picker (oldest → newest)
  const availableMonths = useMemo(() =>
    [...summaries]
      .sort((a, b) => (a.year * 12 + a.month) - (b.year * 12 + b.month))
      .map(s => ({ value: toYearMonth(s), label: `${MONTH_NAMES[s.month - 1]} ${s.year}` })),
    [summaries]
  )

  // Summaries filtered to selected range
  const filtered = useMemo(() => {
    if (!fromMonth || !toMonth) return summaries
    const [fy, fm] = fromMonth.split('-').map(Number)
    const [ty, tm] = toMonth.split('-').map(Number)
    return summaries.filter(s => {
      const v = s.year * 12 + s.month
      return v >= fy * 12 + fm && v <= ty * 12 + tm
    })
  }, [summaries, fromMonth, toMonth])

  // Bar chart: oldest → newest
  const barData = useMemo(() =>
    [...filtered].reverse().map(s => ({
      label:    `${MONTH_NAMES[s.month - 1]} ${s.year}`,
      income:   Number(s.totalIncome),
      expenses: Number(s.totalExpenses),
    })),
    [filtered]
  )

  // Savings rate line: oldest → newest
  const savingsData = useMemo(() =>
    [...filtered].reverse().map(s => {
      const income  = Number(s.totalIncome)
      const savings = Number(s.netSavings)
      const rate    = income > 0 ? Math.max(0, (savings / income) * 100) : 0
      return {
        label:   `${MONTH_NAMES[s.month - 1]} ${s.year}`,
        rate:    parseFloat(rate.toFixed(1)),
        savings,
        income,
      }
    }),
    [filtered]
  )

  // Top 10 per tab
  const topExpenses = useMemo(() =>
    topTx.filter(t => EXPENSE_CLS.has(t.classification))
         .sort((a, b) => Number(b.amount) - Number(a.amount))
         .slice(0, 10),
    [topTx]
  )
  const topIncome = useMemo(() =>
    topTx.filter(t => INCOME_CLS.has(t.classification))
         .sort((a, b) => Number(b.amount) - Number(a.amount))
         .slice(0, 10),
    [topTx]
  )

  const categoryTotal = categories.reduce((sum, c) => sum + Number(c.total), 0)
  const hasData = summaries.length > 0
  const currentList = activeTab === 'expenses' ? topExpenses : topIncome

  if (loading) {
    return (
      <div className="p-8 flex items-center justify-center h-64">
        <p className="text-white/30 text-sm">Loading analytics…</p>
      </div>
    )
  }

  return (
    <div className="p-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-white text-2xl font-bold">Analytics</h1>
        <p className="text-white/40 text-sm mt-1">Detailed breakdown of your finances</p>
      </div>

      {/* Month range picker */}
      {hasData && (
        <div className="flex items-center gap-3 mb-8 flex-wrap">
          <span className="text-white/40 text-sm">From</span>
          <select
            value={fromMonth}
            onChange={e => setFromMonth(e.target.value)}
            className="bg-white/[0.05] border border-white/10 rounded-lg px-3 py-1.5 text-white text-sm focus:outline-none focus:border-brand-500/50"
          >
            {availableMonths.map(m => (
              <option key={m.value} value={m.value} className="bg-gray-900">{m.label}</option>
            ))}
          </select>
          <span className="text-white/40 text-sm">To</span>
          <select
            value={toMonth}
            onChange={e => setToMonth(e.target.value)}
            className="bg-white/[0.05] border border-white/10 rounded-lg px-3 py-1.5 text-white text-sm focus:outline-none focus:border-brand-500/50"
          >
            {availableMonths.map(m => (
              <option key={m.value} value={m.value} className="bg-gray-900">{m.label}</option>
            ))}
          </select>
        </div>
      )}

      {!hasData ? (
        <div className="bg-white/[0.03] border border-white/5 border-dashed rounded-2xl p-12 text-center">
          <div className="w-12 h-12 rounded-2xl bg-brand-950 border border-brand-800/40 flex items-center justify-center mx-auto mb-4">
            <svg className="w-6 h-6 text-brand-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
          </div>
          <h3 className="text-white font-semibold mb-2">No data yet</h3>
          <p className="text-white/30 text-sm max-w-xs mx-auto">
            Upload and process a bank statement to see your analytics.
          </p>
        </div>
      ) : (
        <div className="space-y-6">
          {/* Row 1: Pie + Bar */}
          <div className="grid grid-cols-1 xl:grid-cols-2 gap-5">

            {/* Section 1 — Spending by Category */}
            <div className="bg-white/[0.03] border border-white/5 rounded-2xl p-6">
              <h2 className="text-white font-semibold mb-5">Spending by Category</h2>
              {categories.length === 0 ? (
                <div className="h-48 flex items-center justify-center">
                  <p className="text-white/20 text-sm">No expense data for this period</p>
                </div>
              ) : (
                <>
                  <ResponsiveContainer width="100%" height={220}>
                    <PieChart>
                      <Pie
                        data={categories}
                        dataKey="total"
                        nameKey="category"
                        cx="50%"
                        cy="50%"
                        innerRadius={60}
                        outerRadius={90}
                        paddingAngle={2}
                      >
                        {categories.map((_, i) => (
                          <Cell key={i} fill={SLICE_COLORS[i % SLICE_COLORS.length]} />
                        ))}
                      </Pie>
                      <Tooltip
                        contentStyle={{
                          backgroundColor: '#111827',
                          border: '1px solid rgba(255,255,255,0.1)',
                          borderRadius: '12px',
                          color: '#fff',
                          fontSize: '12px',
                        }}
                        formatter={(value, name) => [fmt(value), name]}
                      />
                    </PieChart>
                  </ResponsiveContainer>
                  <div className="mt-3 space-y-1.5 max-h-40 overflow-y-auto pr-1">
                    {categories.map((c, i) => (
                      <div key={c.category} className="flex items-center justify-between text-xs">
                        <div className="flex items-center gap-2">
                          <span
                            className="w-2.5 h-2.5 rounded-full shrink-0"
                            style={{ backgroundColor: SLICE_COLORS[i % SLICE_COLORS.length] }}
                          />
                          <span className="text-white/60">{c.category}</span>
                        </div>
                        <div className="flex items-center gap-3 text-white/40">
                          <span>{fmt(Number(c.total))}</span>
                          <span className="w-10 text-right">
                            {categoryTotal > 0 ? ((Number(c.total) / categoryTotal) * 100).toFixed(1) : 0}%
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </div>

            {/* Section 2 — Income vs Expenses */}
            <div className="bg-white/[0.03] border border-white/5 rounded-2xl p-6">
              <h2 className="text-white font-semibold mb-5">Income vs Expenses</h2>
              {barData.length === 0 ? (
                <div className="h-48 flex items-center justify-center">
                  <p className="text-white/20 text-sm">No data for this period</p>
                </div>
              ) : (
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={barData} margin={{ top: 5, right: 5, bottom: 5, left: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                    <XAxis
                      dataKey="label"
                      tick={{ fill: 'rgba(255,255,255,0.3)', fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <YAxis
                      tickFormatter={fmtK}
                      tick={{ fill: 'rgba(255,255,255,0.3)', fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                      width={48}
                    />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: '#111827',
                        border: '1px solid rgba(255,255,255,0.1)',
                        borderRadius: '12px',
                        color: '#fff',
                        fontSize: '12px',
                      }}
                      formatter={(value, name) => [fmt(value), name.charAt(0).toUpperCase() + name.slice(1)]}
                    />
                    <Bar dataKey="income"   fill="#4ade80" radius={[4, 4, 0, 0]} name="income" />
                    <Bar dataKey="expenses" fill="#f87171" radius={[4, 4, 0, 0]} name="expenses" />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>

          {/* Row 2: Savings Rate + Top Transactions */}
          <div className="grid grid-cols-1 xl:grid-cols-2 gap-5">

            {/* Section 3 — Savings Rate */}
            <div className="bg-white/[0.03] border border-white/5 rounded-2xl p-6">
              <h2 className="text-white font-semibold mb-5">Savings Rate</h2>
              {savingsData.length === 0 ? (
                <div className="h-48 flex items-center justify-center">
                  <p className="text-white/20 text-sm">No data for this period</p>
                </div>
              ) : (
                <ResponsiveContainer width="100%" height={220}>
                  <LineChart data={savingsData} margin={{ top: 5, right: 5, bottom: 5, left: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                    <XAxis
                      dataKey="label"
                      tick={{ fill: 'rgba(255,255,255,0.3)', fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <YAxis
                      domain={[0, 100]}
                      tickFormatter={v => `${v}%`}
                      tick={{ fill: 'rgba(255,255,255,0.3)', fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                      width={40}
                    />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: '#111827',
                        border: '1px solid rgba(255,255,255,0.1)',
                        borderRadius: '12px',
                        color: '#fff',
                        fontSize: '12px',
                      }}
                      formatter={(value, _name, props) => [
                        `${value}% · ${fmt(props.payload.savings)} of ${fmt(props.payload.income)}`,
                        'Savings Rate',
                      ]}
                    />
                    <Line
                      type="monotone"
                      dataKey="rate"
                      stroke="#818cf8"
                      strokeWidth={2}
                      dot={{ fill: '#818cf8', r: 3 }}
                      name="Savings Rate"
                    />
                  </LineChart>
                </ResponsiveContainer>
              )}
            </div>

            {/* Section 4 — Top Transactions */}
            <div className="bg-white/[0.03] border border-white/5 rounded-2xl p-6">
              <div className="flex items-center justify-between mb-5">
                <h2 className="text-white font-semibold">Top Transactions</h2>
                <div className="flex bg-white/[0.05] rounded-lg p-0.5">
                  <button
                    onClick={() => setActiveTab('expenses')}
                    className={`px-3 py-1 rounded-md text-xs font-medium transition-colors ${
                      activeTab === 'expenses'
                        ? 'bg-red-500/20 text-red-400'
                        : 'text-white/40 hover:text-white/60'
                    }`}
                  >
                    Top Expenses
                  </button>
                  <button
                    onClick={() => setActiveTab('income')}
                    className={`px-3 py-1 rounded-md text-xs font-medium transition-colors ${
                      activeTab === 'income'
                        ? 'bg-green-500/20 text-green-400'
                        : 'text-white/40 hover:text-white/60'
                    }`}
                  >
                    Top Income
                  </button>
                </div>
              </div>
              <div className="space-y-1">
                {currentList.length === 0 ? (
                  <div className="h-32 flex items-center justify-center">
                    <p className="text-white/20 text-sm">No transactions for this period</p>
                  </div>
                ) : (
                  currentList.map((t, i) => (
                    <div
                      key={i}
                      className="flex items-center justify-between py-2 border-b border-white/[0.04] last:border-0"
                    >
                      <div className="flex-1 min-w-0 pr-3">
                        <p className="text-white/70 text-sm truncate">{t.description}</p>
                        <div className="flex items-center gap-2 mt-0.5">
                          <span className="text-white/25 text-xs">{t.date}</span>
                          {t.category && (
                            <span className="px-1.5 py-0.5 rounded text-[10px] bg-white/[0.05] text-white/40">
                              {t.category}
                            </span>
                          )}
                        </div>
                      </div>
                      <span className={`text-sm font-medium shrink-0 ${
                        activeTab === 'expenses' ? 'text-red-400' : 'text-green-400'
                      }`}>
                        {activeTab === 'expenses' ? '−' : '+'}{fmt(Number(t.amount))}
                      </span>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom' // used for Goals "View all" link
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'
import { getSavings } from '../api/summaries'
import { getGoals } from '../api/goals'

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

function fmt(n) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(n)
}

export default function DashboardPage() {
  const user = JSON.parse(localStorage.getItem('user') || '{}')
  const [summaries, setSummaries] = useState([])
  const [goals, setGoals] = useState([])
  const [loading, setLoading] = useState(true)
  const [includeExtra, setIncludeExtra] = useState(false)

  useEffect(() => {
    Promise.all([getSavings(), getGoals()])
      .then(([s, g]) => { setSummaries(s); setGoals(g) })
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  const totalIncome   = summaries.reduce((sum, s) => sum + Number(s.totalIncome),   0)
  const totalExpenses = summaries.reduce((sum, s) => sum + Number(s.totalExpenses), 0)
  const totalExtraIn  = summaries.reduce((sum, s) => sum + Number(s.totalExtraIn ?? 0), 0)
  const baseNetSavings = summaries.reduce((sum, s) => sum + Number(s.netSavings), 0)
  const netSavings    = includeExtra ? baseNetSavings + totalExtraIn : baseNetSavings
  const hasData = summaries.length > 0

  // Chart: oldest → newest on x-axis
  const chartData = [...summaries].reverse().map(s => ({
    label:    `${MONTH_NAMES[s.month - 1]} ${s.year}`,
    income:   Number(s.totalIncome),
    expenses: Number(s.totalExpenses),
    savings:  includeExtra
      ? Number(s.netSavings) + Number(s.totalExtraIn ?? 0)
      : Number(s.netSavings),
  }))

  const activeGoals = goals.filter(g => g.status === 'ACTIVE').slice(0, 3)

  const plainCards = [
    {
      label: 'Total Income',
      value: hasData ? fmt(totalIncome) : '—',
      sub:   hasData ? `Across ${summaries.length} month${summaries.length !== 1 ? 's' : ''}` : 'No statements processed yet',
      color: hasData ? 'text-green-400' : 'text-white',
    },
    {
      label: 'Total Expenses',
      value: hasData ? fmt(totalExpenses) : '—',
      sub:   hasData ? `Across ${summaries.length} month${summaries.length !== 1 ? 's' : ''}` : 'No statements processed yet',
      color: hasData ? 'text-red-400' : 'text-white',
    },
  ]

  return (
    <div className="p-8">
      {/* Header */}
      <div className="mb-10">
        <h1 className="text-white text-2xl font-bold">
          Good to see you, {user.name ? user.name.split(' ')[0] : 'there'} 👋
        </h1>
        <p className="text-white/40 text-sm mt-1">Here is your financial overview</p>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5 mb-8">
        {plainCards.map((card) => (
          <div key={card.label} className="bg-white/[0.03] border border-white/5 rounded-2xl p-6">
            <p className="text-white/40 text-sm mb-2">{card.label}</p>
            <p className={`text-3xl font-bold mb-1 ${card.color}`}>{card.value}</p>
            <p className="text-white/25 text-xs">{card.sub}</p>
          </div>
        ))}

        {/* Net Savings card with toggle */}
        <div className="bg-white/[0.03] border border-white/5 rounded-2xl p-6">
          <div className="flex items-center justify-between mb-2">
            <p className="text-white/40 text-sm">Net Savings</p>
            {hasData && totalExtraIn > 0 && (
              <button
                onClick={() => setIncludeExtra(v => !v)}
                title={includeExtra ? 'Excluding extra income' : 'Include extra income'}
                className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[10px] font-medium border transition-colors ${
                  includeExtra
                    ? 'bg-amber-500/15 border-amber-500/30 text-amber-400'
                    : 'bg-white/[0.04] border-white/10 text-white/35 hover:text-white/55'
                }`}
              >
                <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
                {includeExtra ? `Extra ${fmt(totalExtraIn)} on` : 'Extra income'}
              </button>
            )}
          </div>
          <p className={`text-3xl font-bold mb-1 ${!hasData ? 'text-white' : netSavings >= 0 ? 'text-brand-400' : 'text-red-400'}`}>
            {hasData ? fmt(netSavings) : '—'}
          </p>
          <p className="text-white/25 text-xs">
            {!hasData
              ? 'No statements processed yet'
              : netSavings >= 0 ? "You're saving well!" : 'Spending exceeds income'}
          </p>
        </div>
      </div>

      {/* Chart + Goals row */}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-5 mb-8">
        {/* Savings trend chart */}
        <div className="xl:col-span-2 bg-white/[0.03] border border-white/5 rounded-2xl p-6">
          <h2 className="text-white font-semibold mb-5">Monthly Trend</h2>
          {hasData ? (
            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={chartData} margin={{ top: 5, right: 5, bottom: 5, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                <XAxis
                  dataKey="label"
                  tick={{ fill: 'rgba(255,255,255,0.3)', fontSize: 11 }}
                  axisLine={false}
                  tickLine={false}
                />
                <YAxis
                  tickFormatter={(v) => `$${Math.abs(v) >= 1000 ? (v / 1000).toFixed(0) + 'k' : v}`}
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
                <Line type="monotone" dataKey="income"   stroke="#4ade80" strokeWidth={2} dot={false} name="income" />
                <Line type="monotone" dataKey="expenses" stroke="#f87171" strokeWidth={2} dot={false} name="expenses" />
                <Line type="monotone" dataKey="savings"  stroke="#818cf8" strokeWidth={2} dot={{ fill: '#818cf8', r: 3 }} name="savings" />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-48 flex items-center justify-center">
              <p className="text-white/20 text-sm">Upload and process a statement to see your trend</p>
            </div>
          )}
        </div>

        {/* Goals progress */}
        <div className="bg-white/[0.03] border border-white/5 rounded-2xl p-6">
          <div className="flex items-center justify-between mb-5">
            <h2 className="text-white font-semibold">Goals</h2>
            <Link to="/dashboard/goals" className="text-brand-400 text-xs hover:text-brand-300 transition-colors">
              View all
            </Link>
          </div>
          {activeGoals.length > 0 ? (
            <div className="space-y-5">
              {activeGoals.map(goal => (
                <div key={goal.id}>
                  <div className="flex items-center justify-between mb-1.5">
                    <span className="text-white/70 text-sm truncate pr-2">{goal.title}</span>
                    <span className="text-white/40 text-xs shrink-0">{Number(goal.progressPercent).toFixed(0)}%</span>
                  </div>
                  <div className="h-1.5 rounded-full bg-white/[0.06] overflow-hidden">
                    <div
                      className="h-full rounded-full bg-brand-500 transition-all"
                      style={{ width: `${Math.min(Number(goal.progressPercent), 100)}%` }}
                    />
                  </div>
                  <div className="flex justify-between mt-1">
                    <span className="text-white/25 text-xs">{fmt(Number(goal.currentAmount))}</span>
                    <span className="text-white/25 text-xs">{fmt(Number(goal.targetAmount))}</span>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="h-48 flex flex-col items-center justify-center gap-3">
              <p className="text-white/20 text-sm">No active goals yet</p>
              <Link to="/dashboard/goals" className="text-brand-400 text-xs hover:text-brand-300 transition-colors">
                Add a goal →
              </Link>
            </div>
          )}
        </div>
      </div>

      {/* Empty state — only if no data */}
      {!hasData && !loading && (
        <div className="bg-white/[0.03] border border-white/5 border-dashed rounded-2xl p-12 text-center">
          <div className="w-12 h-12 rounded-2xl bg-brand-950 border border-brand-800/40 flex items-center justify-center mx-auto mb-4">
            <svg className="w-6 h-6 text-brand-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
          </div>
          <h3 className="text-white font-semibold mb-2">No statements yet</h3>
          <p className="text-white/30 text-sm max-w-xs mx-auto">
            Upload and process a bank statement to start seeing your transactions and insights here.
          </p>
        </div>
      )}
    </div>
  )
}

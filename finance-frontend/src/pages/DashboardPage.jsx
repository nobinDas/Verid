export default function DashboardPage() {
  const user = JSON.parse(localStorage.getItem('user') || '{}')

  return (
    <div className="p-8">
      {/* Header */}
      <div className="mb-10">
        <h1 className="text-white text-2xl font-bold">
          Good to see you, {user.name ? user.name.split(' ')[0] : 'there'} 👋
        </h1>
        <p className="text-white/40 text-sm mt-1">Here is your financial overview</p>
      </div>

      {/* Placeholder cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5 mb-8">
        {[
          { label: 'Total Income', value: '—', sub: 'No statements uploaded yet' },
          { label: 'Total Expenses', value: '—', sub: 'No statements uploaded yet' },
          { label: 'Net Savings', value: '—', sub: 'No statements uploaded yet' },
        ].map((card) => (
          <div key={card.label} className="bg-white/[0.03] border border-white/5 rounded-2xl p-6">
            <p className="text-white/40 text-sm mb-2">{card.label}</p>
            <p className="text-white text-3xl font-bold mb-1">{card.value}</p>
            <p className="text-white/25 text-xs">{card.sub}</p>
          </div>
        ))}
      </div>

      {/* Empty state */}
      <div className="bg-white/[0.03] border border-white/5 border-dashed rounded-2xl p-12 text-center">
        <div className="w-12 h-12 rounded-2xl bg-brand-950 border border-brand-800/40 flex items-center justify-center mx-auto mb-4">
          <svg className="w-6 h-6 text-brand-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
        </div>
        <h3 className="text-white font-semibold mb-2">No statements yet</h3>
        <p className="text-white/30 text-sm max-w-xs mx-auto">
          Upload your first bank statement to start seeing your transactions and insights here.
        </p>
      </div>
    </div>
  )
}

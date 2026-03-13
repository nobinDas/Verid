import { Link } from 'react-router-dom'

export default function Hero() {
  return (
    <section className="relative min-h-screen flex items-center justify-center overflow-hidden pt-20">

      {/* Background glow effects */}
      <div className="absolute inset-0 z-0">
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 w-[700px] h-[700px] bg-brand-700/20 rounded-full blur-[120px]" />
        <div className="absolute bottom-0 left-1/4 w-[400px] h-[400px] bg-brand-900/30 rounded-full blur-[100px]" />
        {/* Grid overlay */}
        <div
          className="absolute inset-0 opacity-[0.04]"
          style={{
            backgroundImage: `linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px)`,
            backgroundSize: '60px 60px',
          }}
        />
      </div>

      <div className="relative z-10 max-w-4xl mx-auto px-6 text-center">

        {/* Badge */}
        <div className="inline-flex items-center gap-2 bg-brand-950/80 border border-brand-700/40 rounded-full px-4 py-1.5 mb-8">
          <span className="w-2 h-2 bg-brand-400 rounded-full animate-pulse" />
          <span className="text-brand-300 text-sm font-medium">Your Financial Intelligence Platform</span>
        </div>

        {/* Headline */}
        <h1 className="text-5xl md:text-7xl font-extrabold leading-tight tracking-tight mb-6">
          The Start of a{' '}
          <span className="bg-gradient-to-r from-brand-400 via-brand-300 to-cyan-400 bg-clip-text text-transparent">
            Better Financial
          </span>
          <br />
          Future
        </h1>

        {/* Subheadline */}
        <p className="text-white/50 text-lg md:text-xl font-light max-w-2xl mx-auto mb-10 leading-relaxed">
          Upload your bank statements, set your goals, and let AI turn your raw transactions
          into clear, actionable insights — all in one place.
        </p>

        {/* CTA buttons */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
          <Link
            to="/signup"
            className="bg-brand-600 hover:bg-brand-500 text-white font-semibold px-8 py-3.5 rounded-xl text-base transition-all duration-200 shadow-lg shadow-brand-900/50 hover:shadow-brand-700/40"
          >
            Get Started Free
          </Link>
          <a
            href="#demo"
            className="flex items-center gap-2 text-white/70 hover:text-white font-medium px-6 py-3.5 rounded-xl border border-white/10 hover:border-white/20 text-base transition-all duration-200 group"
          >
            <span className="w-8 h-8 rounded-full bg-white/10 group-hover:bg-white/15 flex items-center justify-center transition-colors">
              <svg className="w-3 h-3 ml-0.5" fill="currentColor" viewBox="0 0 24 24">
                <path d="M8 5v14l11-7z" />
              </svg>
            </span>
            Watch Demo
          </a>
        </div>

        {/* Stats row — hidden until real numbers are available */}
        {/* <div className="mt-20 grid grid-cols-3 gap-8 max-w-lg mx-auto border-t border-white/5 pt-10">
          {[
            { label: 'Transactions Analyzed', value: '10M+' },
            { label: 'Users', value: '50K+' },
            { label: 'Goals Reached', value: '98%' },
          ].map((stat) => (
            <div key={stat.label} className="text-center">
              <div className="text-2xl font-bold text-white">{stat.value}</div>
              <div className="text-white/40 text-xs mt-1">{stat.label}</div>
            </div>
          ))}
        </div> */}
      </div>

      {/* Bottom fade */}
      <div className="absolute bottom-0 left-0 right-0 h-32 bg-gradient-to-t from-[#0a0e1a] to-transparent z-10" />
    </section>
  )
}

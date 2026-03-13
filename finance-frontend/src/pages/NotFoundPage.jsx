import { Link, useNavigate } from 'react-router-dom'

export default function NotFoundPage() {
  const navigate = useNavigate()

  return (
    <div className="min-h-screen bg-[#0a0e1a] flex items-center justify-center px-4">

      {/* Background glow */}
      <div className="absolute top-1/3 left-1/2 -translate-x-1/2 w-[500px] h-[500px] bg-brand-800/20 rounded-full blur-[120px] pointer-events-none" />

      <div className="relative text-center">

        {/* Logo */}
        <Link to="/" className="inline-flex items-center gap-2 mb-12">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center">
            <span className="text-white font-black text-base">V</span>
          </div>
          <span className="text-white font-bold text-2xl tracking-tight">verid</span>
        </Link>

        {/* 404 */}
        <div className="text-8xl font-black text-white/5 leading-none mb-4 select-none">404</div>

        <h1 className="text-white text-2xl font-bold mb-3">Page not found</h1>
        <p className="text-white/40 text-sm mb-8 max-w-xs mx-auto">
          The page you are looking for does not exist or has been moved.
        </p>

        <button
          onClick={() => navigate(-1)}
          className="inline-block bg-brand-600 hover:bg-brand-500 text-white font-semibold px-6 py-3 rounded-xl text-sm transition-colors duration-200"
        >
          Go Back
        </button>

      </div>
    </div>
  )
}

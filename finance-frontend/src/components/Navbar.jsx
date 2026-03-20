import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

export default function Navbar() {
  const navigate = useNavigate()
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  const isLoggedIn = !!localStorage.getItem('token') && !!user
  const [dropOpen, setDropOpen] = useState(false)
  const dropRef = useRef(null)

  useEffect(() => {
    if (!dropOpen) return
    function handleClick(e) {
      if (dropRef.current && !dropRef.current.contains(e.target)) setDropOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [dropOpen])

  function handleLogout() {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    setDropOpen(false)
    navigate('/')
  }

  return (
    <nav className="fixed top-0 left-0 right-0 z-50 bg-[#0a0e1a]/90 backdrop-blur-md border-b border-white/5">
      <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">

        {/* Logo */}
        <Link to="/" className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center">
            <span className="text-white font-black text-sm">V</span>
          </div>
          <span className="text-white font-bold text-xl tracking-tight">verid</span>
        </Link>

        {/* Nav links */}
        <div className="flex items-center gap-2">
          <a
            href="#about"
            className="text-white/60 hover:text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors duration-200"
          >
            About
          </a>

          {isLoggedIn ? (
            <div className="relative" ref={dropRef}>
              {/* Avatar button */}
              <button
                onClick={() => setDropOpen((o) => !o)}
                className="w-9 h-9 rounded-full bg-brand-700/50 hover:bg-brand-700/70 border border-brand-600/30 flex items-center justify-center text-brand-300 font-semibold text-sm transition-colors duration-200"
              >
                {user.name.charAt(0).toUpperCase()}
              </button>

              {/* Dropdown */}
              {dropOpen && (
                <div className="absolute right-0 mt-2 w-44 bg-[#111827] border border-white/10 rounded-xl shadow-2xl overflow-hidden">
                  <div className="px-3 py-2.5 border-b border-white/5">
                    <p className="text-white text-sm font-medium truncate">{user.name}</p>
                    <p className="text-white/30 text-xs truncate">{user.email}</p>
                  </div>
                  <Link
                    to="/dashboard"
                    onClick={() => setDropOpen(false)}
                    className="flex items-center gap-2.5 w-full px-3 py-2.5 text-sm text-white/60 hover:text-white hover:bg-white/[0.04] transition-colors"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
                    </svg>
                    Dashboard
                  </Link>
                  <button
                    onClick={handleLogout}
                    className="flex items-center gap-2.5 w-full px-3 py-2.5 text-sm text-white/60 hover:text-red-400 hover:bg-red-500/5 transition-colors"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                    </svg>
                    Log Out
                  </button>
                </div>
              )}
            </div>
          ) : (
            <>
              <Link
                to="/login"
                className="text-white/60 hover:text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors duration-200"
              >
                Login
              </Link>
              <Link
                to="/signup"
                className="bg-brand-600 hover:bg-brand-500 text-white text-sm font-semibold px-5 py-2 rounded-lg transition-colors duration-200"
              >
                Sign Up
              </Link>
            </>
          )}
        </div>
      </div>
    </nav>
  )
}

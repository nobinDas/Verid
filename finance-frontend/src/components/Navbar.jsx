import { Link, useNavigate } from 'react-router-dom'

export default function Navbar() {
  const navigate = useNavigate()
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  const isLoggedIn = !!localStorage.getItem('token') && !!user

  function handleLogout() {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
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
            <>
              {/* User avatar + name */}
              <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-white/[0.04] border border-white/8">
                <div className="w-6 h-6 rounded-full bg-brand-700/60 flex items-center justify-center text-brand-300 font-semibold text-xs">
                  {user.name.charAt(0).toUpperCase()}
                </div>
                <span className="text-white/70 text-sm font-medium">{user.name.split(' ')[0]}</span>
              </div>

              <Link
                to="/dashboard"
                className="text-white/70 hover:text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors duration-200"
              >
                Dashboard
              </Link>
              <button
                onClick={handleLogout}
                className="text-white/50 hover:text-red-400 text-sm font-medium px-4 py-2 rounded-lg transition-colors duration-200"
              >
                Log Out
              </button>
            </>
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

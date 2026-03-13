export default function Navbar() {
  return (
    <nav className="fixed top-0 left-0 right-0 z-50 bg-[#0a0e1a]/90 backdrop-blur-md border-b border-white/5">
      <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">

        {/* Logo */}
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center">
            <span className="text-white font-black text-sm">V</span>
          </div>
          <span className="text-white font-bold text-xl tracking-tight">verid</span>
        </div>

        {/* Nav links */}
        <div className="flex items-center gap-2">
          <a
            href="#about"
            className="text-white/60 hover:text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors duration-200"
          >
            About
          </a>
          <a
            href="/login"
            className="text-white/60 hover:text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors duration-200"
          >
            Login
          </a>
          <a
            href="/signup"
            className="bg-brand-600 hover:bg-brand-500 text-white text-sm font-semibold px-5 py-2 rounded-lg transition-colors duration-200"
          >
            Sign Up
          </a>
        </div>
      </div>
    </nav>
  )
}

export default function Footer() {
  return (
    <footer className="bg-[#080b15] border-t border-white/5 py-10 px-6">
      <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center">
            <span className="text-white font-black text-xs">V</span>
          </div>
          <span className="text-white font-bold text-lg tracking-tight">verid</span>
        </div>
        <p className="text-white/30 text-sm">
          © {new Date().getFullYear()} verid. All rights reserved.
        </p>
        <div className="flex items-center gap-6">
          <a href="#" className="text-white/30 hover:text-white/60 text-sm transition-colors">Privacy</a>
          <a href="#" className="text-white/30 hover:text-white/60 text-sm transition-colors">Terms</a>
          <a href="#about" className="text-white/30 hover:text-white/60 text-sm transition-colors">About</a>
        </div>
      </div>
    </footer>
  )
}

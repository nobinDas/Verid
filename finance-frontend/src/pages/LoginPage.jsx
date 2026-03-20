import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { login } from '../api/auth'

export default function LoginPage() {
  const navigate = useNavigate()

  const [form, setForm] = useState({
    email: '',
    password: '',
  })

  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  function handleChange(e) {
    setForm({ ...form, [e.target.name]: e.target.value })
    setError('')
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setLoading(true)
    try {
      const data = await login({
        email: form.email,
        password: form.password,
      })
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify({ email: data.email, name: data.name }))
      navigate('/dashboard')
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-[#0a0e1a] flex items-center justify-center px-4 py-16">

      {/* Background glow */}
      <div className="absolute top-1/3 left-1/2 -translate-x-1/2 w-[500px] h-[500px] bg-brand-800/20 rounded-full blur-[120px] pointer-events-none" />

      <div className="relative w-full max-w-md">

        {/* Logo */}
        <Link to="/" className="flex items-center justify-center gap-2 mb-8">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center">
            <span className="text-white font-black text-base">V</span>
          </div>
          <span className="text-white font-bold text-2xl tracking-tight">verid</span>
        </Link>

        {/* Card */}
        <div className="bg-white/[0.04] border border-white/8 rounded-2xl p-8">

          <h1 className="text-white text-2xl font-bold mb-1">Welcome back</h1>
          <p className="text-white/40 text-sm mb-7">Log in to your verid account</p>

          {/* Error message */}
          {error && (
            <div className="bg-red-500/10 border border-red-500/30 text-red-400 text-sm rounded-xl px-4 py-3 mb-5">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">

            {/* Email */}
            <div>
              <label className="text-white/60 text-sm font-medium block mb-1.5">
                Email Address
              </label>
              <input
                type="email"
                name="email"
                value={form.email}
                onChange={handleChange}
                placeholder="john@example.com"
                required
                className="w-full bg-white/[0.05] border border-white/10 focus:border-brand-500 text-white placeholder-white/20 rounded-xl px-4 py-3 text-sm outline-none transition-colors"
              />
            </div>

            {/* Password */}
            <div>
              <label className="text-white/60 text-sm font-medium block mb-1.5">
                Password
              </label>
              <input
                type="password"
                name="password"
                value={form.password}
                onChange={handleChange}
                placeholder="Enter your password"
                required
                className="w-full bg-white/[0.05] border border-white/10 focus:border-brand-500 text-white placeholder-white/20 rounded-xl px-4 py-3 text-sm outline-none transition-colors"
              />
            </div>

            {/* Submit */}
            <button
              type="submit"
              disabled={loading}
              className="w-full bg-brand-600 hover:bg-brand-500 disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold py-3 rounded-xl text-sm transition-colors duration-200 mt-2"
            >
              {loading ? 'Logging in...' : 'Log In'}
            </button>

          </form>

          {/* Divider */}
          <div className="flex items-center gap-3 my-6">
            <div className="flex-1 h-px bg-white/8" />
            <span className="text-white/25 text-xs">don&apos;t have an account?</span>
            <div className="flex-1 h-px bg-white/8" />
          </div>

          {/* Signup link */}
          <Link
            to="/signup"
            className="block w-full text-center border border-white/10 hover:border-white/20 text-white/60 hover:text-white font-medium py-3 rounded-xl text-sm transition-all duration-200"
          >
            Create Account
          </Link>

        </div>

        {/* Footer note */}
        <p className="text-white/20 text-xs text-center mt-6">
          Your account is protected by secure authentication
        </p>

      </div>
    </div>
  )
}

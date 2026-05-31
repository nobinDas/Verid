import { useState, useRef, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { sendMessage } from '../api/chat'

const MINI_SUGGESTIONS = [
  'What did I spend the most on?',
  'How are my savings looking?',
  'Am I on track with my goals?',
]

function renderText(text) {
  const parts = text.split(/(\*\*[^*]+\*\*)/g)
  return parts.map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      return <strong key={i}>{part.slice(2, -2)}</strong>
    }
    return part.split('\n').map((line, j, arr) => (
      <span key={`${i}-${j}`}>{line}{j < arr.length - 1 && <br />}</span>
    ))
  })
}

function TypingDots() {
  return (
    <div className="flex items-end gap-1 px-1 py-0.5">
      {[0, 1, 2].map(i => (
        <span key={i} className="w-1.5 h-1.5 rounded-full bg-white/40 animate-bounce"
          style={{ animationDelay: `${i * 0.15}s` }} />
      ))}
    </div>
  )
}

export default function ChatBubble() {
  const [open, setOpen]       = useState(false)
  const [messages, setMessages] = useState([])
  const [input, setInput]     = useState('')
  const [loading, setLoading] = useState(false)
  const bottomRef  = useRef(null)
  const inputRef   = useRef(null)
  const navigate   = useNavigate()
  const location   = useLocation()

  // Hide on the full chat page — no need for the bubble there
  if (location.pathname === '/dashboard/chat') return null

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  useEffect(() => {
    if (open) setTimeout(() => inputRef.current?.focus(), 150)
  }, [open])

  async function handleSend(text) {
    const msg = (text ?? input).trim()
    if (!msg || loading) return
    setInput('')
    setMessages(prev => [...prev, { role: 'user', text: msg }])
    setLoading(true)
    try {
      const data = await sendMessage(msg)
      setMessages(prev => [...prev, { role: 'assistant', text: data.reply }])
    } catch {
      setMessages(prev => [...prev, { role: 'assistant', text: 'Sorry, something went wrong. Try again.' }])
    } finally {
      setLoading(false)
    }
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend() }
  }

  function expandToFullScreen() {
    navigate('/dashboard/chat')
    setOpen(false)
  }

  return (
    <>
      {/* ── Mini chat panel ─────────────────────────────────────────────── */}
      {open && (
        <div
          className="fixed bottom-24 right-6 z-50 w-[340px] flex flex-col bg-[#111827] border border-white/10 rounded-2xl shadow-2xl overflow-hidden"
          style={{ height: '460px' }}
        >
          {/* Header */}
          <div className="flex items-center justify-between px-4 py-3 border-b border-white/5 shrink-0">
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-full bg-brand-600 flex items-center justify-center">
                <svg className="w-3.5 h-3.5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
                </svg>
              </div>
              <div>
                <p className="text-white text-sm font-medium leading-tight">Finance Assistant</p>
                <p className="text-white/30 text-[10px]">Ask anything about your finances</p>
              </div>
            </div>
            <div className="flex items-center gap-1">
              {/* Expand to full screen */}
              <button
                onClick={expandToFullScreen}
                title="Open full screen"
                className="w-7 h-7 flex items-center justify-center rounded-lg text-white/40 hover:text-white/70 hover:bg-white/[0.06] transition-colors"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M4 8V4m0 0h4M4 4l5 5m11-1V4m0 0h-4m4 0l-5 5M4 16v4m0 0h4m-4 0l5-5m11 5v-4m0 4h-4m4 0l-5-5" />
                </svg>
              </button>
              {/* Close */}
              <button
                onClick={() => setOpen(false)}
                className="w-7 h-7 flex items-center justify-center rounded-lg text-white/40 hover:text-white/70 hover:bg-white/[0.06] transition-colors"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>

          {/* Messages */}
          <div className="flex-1 overflow-y-auto px-3 py-3 space-y-3">
            {messages.length === 0 && !loading ? (
              <div className="flex flex-col items-center justify-center h-full gap-3 text-center px-2">
                <p className="text-white/40 text-xs">Ask me anything about your spending, goals, or savings.</p>
                <div className="flex flex-col gap-1.5 w-full">
                  {MINI_SUGGESTIONS.map(s => (
                    <button
                      key={s}
                      onClick={() => handleSend(s)}
                      className="w-full px-3 py-2 rounded-xl bg-white/[0.04] border border-white/8 text-xs text-white/60 hover:bg-white/[0.07] hover:text-white/80 transition-colors text-left"
                    >
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <>
                {messages.map((m, i) => (
                  <div key={i} className={`flex gap-2 ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                    {m.role === 'assistant' && (
                      <div className="w-6 h-6 rounded-full bg-brand-600 flex-shrink-0 flex items-center justify-center mt-0.5">
                        <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                            d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17H3a2 2 0 01-2-2V5a2 2 0 012-2h16a2 2 0 012 2v10a2 2 0 01-2 2h-2" />
                        </svg>
                      </div>
                    )}
                    <div className={`max-w-[80%] px-3 py-2 rounded-2xl text-xs leading-relaxed ${
                      m.role === 'user'
                        ? 'bg-brand-600 text-white rounded-br-sm'
                        : 'bg-white/[0.06] text-white/85 rounded-bl-sm'
                    }`}>
                      {renderText(m.text)}
                    </div>
                    {m.role === 'user' && (
                      <div className="w-6 h-6 rounded-full bg-white/10 flex-shrink-0 flex items-center justify-center mt-0.5">
                        <svg className="w-3 h-3 text-white/50" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                            d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                        </svg>
                      </div>
                    )}
                  </div>
                ))}
                {loading && (
                  <div className="flex gap-2 justify-start">
                    <div className="w-6 h-6 rounded-full bg-brand-600 flex-shrink-0 flex items-center justify-center mt-0.5">
                      <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                          d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17H3a2 2 0 01-2-2V5a2 2 0 012 2v10a2 2 0 01-2 2h-2" />
                      </svg>
                    </div>
                    <div className="px-3 py-2 rounded-2xl rounded-bl-sm bg-white/[0.06]">
                      <TypingDots />
                    </div>
                  </div>
                )}
              </>
            )}
            <div ref={bottomRef} />
          </div>

          {/* Input */}
          <div className="border-t border-white/5 px-3 py-3 shrink-0">
            <div className="flex items-center gap-2">
              <input
                ref={inputRef}
                value={input}
                onChange={e => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                disabled={loading}
                placeholder="Ask about your finances…"
                className="flex-1 bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2 text-xs text-white placeholder-white/25 focus:outline-none focus:border-brand-500/50 transition-colors disabled:opacity-50"
              />
              <button
                onClick={() => handleSend()}
                disabled={!input.trim() || loading}
                className="w-8 h-8 rounded-xl bg-brand-600 hover:bg-brand-500 disabled:opacity-40 flex items-center justify-center transition-colors shrink-0"
              >
                <svg className="w-3.5 h-3.5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Floating bubble button ──────────────────────────────────────── */}
      <button
        onClick={() => setOpen(o => !o)}
        className={`fixed bottom-6 right-6 z-50 w-14 h-14 rounded-full shadow-lg flex items-center justify-center transition-all duration-200 ${
          open
            ? 'bg-white/10 border border-white/20 text-white/70 hover:bg-white/15'
            : 'bg-brand-600 hover:bg-brand-500 text-white'
        }`}
      >
        {open ? (
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        ) : (
          <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
          </svg>
        )}
      </button>
    </>
  )
}

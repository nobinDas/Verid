import { useState, useRef, useEffect } from 'react'
import { sendMessage } from '../api/chat'

const SUGGESTIONS = [
  'What did I spend the most on last month?',
  'How can I improve my savings rate?',
  'Break down my expenses by category',
  'Am I on track with my financial goals?',
]

function formatTime(date) {
  return date.toLocaleString('en-US', {
    month: 'short', day: 'numeric',
    hour: 'numeric', minute: '2-digit',
  })
}

function renderText(text) {
  // Replace **bold** and newlines
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
    <div className="flex items-end gap-1 px-1 py-1">
      {[0, 1, 2].map(i => (
        <span
          key={i}
          className="w-2 h-2 rounded-full bg-white/40 animate-bounce"
          style={{ animationDelay: `${i * 0.15}s` }}
        />
      ))}
    </div>
  )
}

export default function ChatPage() {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const bottomRef = useRef(null)
  const textareaRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  async function handleSend(text) {
    const msg = (text ?? input).trim()
    if (!msg || loading) return

    setInput('')
    setMessages(prev => [...prev, { role: 'user', text: msg, time: new Date() }])
    setLoading(true)

    try {
      const data = await sendMessage(msg)
      setMessages(prev => [...prev, { role: 'assistant', text: data.reply, time: new Date() }])
    } catch {
      setMessages(prev => [...prev, {
        role: 'assistant',
        text: 'Sorry, something went wrong. Please try again.',
        time: new Date(),
      }])
    } finally {
      setLoading(false)
    }
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  function handleInput(e) {
    setInput(e.target.value)
    const ta = textareaRef.current
    if (ta) {
      ta.style.height = 'auto'
      ta.style.height = Math.min(ta.scrollHeight, 96) + 'px'
    }
  }

  return (
    <div className="flex flex-col h-full bg-gray-950">
      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-6 space-y-4">
        {messages.length === 0 && !loading ? (
          <div className="flex flex-col items-center justify-center h-full gap-6 text-center">
            <div>
              <div className="w-14 h-14 rounded-full bg-brand-600 flex items-center justify-center mx-auto mb-4">
                <svg className="w-7 h-7 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
                </svg>
              </div>
              <h2 className="text-xl font-semibold text-white">Hi, I'm your finance assistant</h2>
              <p className="text-white/50 text-sm mt-1">Ask me anything about your spending, savings, or goals.</p>
            </div>
            <div className="flex flex-wrap gap-2 justify-center max-w-lg">
              {SUGGESTIONS.map(s => (
                <button
                  key={s}
                  onClick={() => handleSend(s)}
                  className="px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-sm text-white/70 hover:bg-white/10 hover:text-white transition-colors text-left"
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <>
            {messages.map((m, i) => (
              <div key={i} className={`flex gap-3 ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                {m.role === 'assistant' && (
                  <div className="w-7 h-7 rounded-full bg-brand-600 flex-shrink-0 flex items-center justify-center mt-1">
                    <svg className="w-4 h-4 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                        d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17H3a2 2 0 01-2-2V5a2 2 0 012-2h16a2 2 0 012 2v10a2 2 0 01-2 2h-2" />
                    </svg>
                  </div>
                )}
                <div className={`max-w-[70%] ${m.role === 'user' ? 'items-end' : 'items-start'} flex flex-col gap-1`}>
                  <div className={`px-4 py-3 rounded-2xl text-sm leading-relaxed ${
                    m.role === 'user'
                      ? 'bg-brand-600 text-white rounded-br-sm'
                      : 'bg-white/5 text-white/90 rounded-bl-sm'
                  }`}>
                    {renderText(m.text)}
                  </div>
                  <span className="text-xs text-white/30 px-1">{formatTime(m.time)}</span>
                </div>
                {m.role === 'user' && (
                  <div className="w-7 h-7 rounded-full bg-white/10 flex-shrink-0 flex items-center justify-center mt-1">
                    <svg className="w-4 h-4 text-white/60" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                        d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                    </svg>
                  </div>
                )}
              </div>
            ))}
            {loading && (
              <div className="flex gap-3 justify-start">
                <div className="w-7 h-7 rounded-full bg-brand-600 flex-shrink-0 flex items-center justify-center mt-1">
                  <svg className="w-4 h-4 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                      d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17H3a2 2 0 01-2-2V5a2 2 0 012-2h16a2 2 0 012 2v10a2 2 0 01-2 2h-2" />
                  </svg>
                </div>
                <div className="px-4 py-3 rounded-2xl rounded-bl-sm bg-white/5">
                  <TypingDots />
                </div>
              </div>
            )}
          </>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input bar */}
      <div className="border-t border-white/10 px-4 py-3 bg-gray-950">
        <div className="flex items-end gap-3 max-w-4xl mx-auto">
          <textarea
            ref={textareaRef}
            value={input}
            onChange={handleInput}
            onKeyDown={handleKeyDown}
            disabled={loading}
            placeholder="Ask about your finances..."
            rows={1}
            className="flex-1 resize-none bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm text-white placeholder-white/30 focus:outline-none focus:border-brand-500 transition-colors disabled:opacity-50"
            style={{ minHeight: '44px', maxHeight: '96px' }}
          />
          <button
            onClick={() => handleSend()}
            disabled={!input.trim() || loading}
            className="flex-shrink-0 w-11 h-11 rounded-xl bg-brand-600 hover:bg-brand-500 disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center transition-colors"
          >
            <svg className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
            </svg>
          </button>
        </div>
        <p className="text-center text-xs text-white/20 mt-2">Enter to send · Shift+Enter for newline</p>
      </div>
    </div>
  )
}

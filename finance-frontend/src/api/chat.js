import { apiFetch, authHeaders } from './client'
const BASE = 'http://localhost:8080/api/chat'

export async function sendMessage(message) {
  const res = await apiFetch(BASE, {
    method: 'POST',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ message }),
  })
  if (!res.ok) throw new Error('Failed to send message')
  return res.json()
}

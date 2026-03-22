import { API_BASE } from './client'
const BASE = `${API_BASE}/api/summaries`

function authHeaders() {
  const token = localStorage.getItem('token')
  return { Authorization: `Bearer ${token}` }
}

export async function getSavings() {
  const res = await fetch(BASE, { headers: authHeaders() })
  if (!res.ok) throw new Error('Failed to fetch summaries')
  return res.json()
}

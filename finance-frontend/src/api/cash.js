import { apiFetch, authHeaders } from './client'

const BASE = 'http://localhost:8080/api/cash'

export async function getCashEntries() {
  const res = await apiFetch(BASE, { headers: authHeaders() })
  if (!res.ok) throw new Error('Failed to fetch cash entries')
  return res.json()
}

export async function createCashEntry(entry) {
  const res = await apiFetch(BASE, {
    method: 'POST',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(entry),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || 'Failed to create cash entry')
  }
  return res.json()
}

export async function deleteCashEntry(id) {
  const res = await apiFetch(`${BASE}/${id}`, { method: 'DELETE', headers: authHeaders() })
  if (!res.ok) throw new Error('Failed to delete cash entry')
}

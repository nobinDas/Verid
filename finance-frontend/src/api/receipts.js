import { apiFetch, authHeaders } from './client'

const BASE = 'http://localhost:8080/api/receipts'

export async function uploadReceipt(file, retentionYears) {
  const form = new FormData()
  form.append('file', file)
  const res = await apiFetch(`${BASE}/upload?retentionYears=${retentionYears}`, {
    method: 'POST',
    headers: authHeaders(),
    body: form,
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || 'Upload failed')
  }
  return res.json()
}

export async function getReceipts() {
  const res = await apiFetch(BASE, { headers: authHeaders() })
  if (!res.ok) throw new Error('Failed to fetch receipts')
  return res.json()
}

export async function deleteReceipt(id) {
  const res = await apiFetch(`${BASE}/${id}`, { method: 'DELETE', headers: authHeaders() })
  if (!res.ok) throw new Error('Delete failed')
}

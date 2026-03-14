import { apiFetch, authHeaders } from './client'

const BASE = 'http://localhost:8080/api/statements'

export async function uploadStatement(file) {
  const form = new FormData()
  form.append('file', file)
  const res = await apiFetch(`${BASE}/upload`, { method: 'POST', headers: authHeaders(), body: form })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || 'Upload failed')
  }
  return res.json()
}

export async function getStatements() {
  const res = await apiFetch(BASE, { headers: authHeaders() })
  if (!res.ok) throw new Error('Failed to fetch statements')
  return res.json()
}

export async function processStatement(id) {
  const res = await apiFetch(`${BASE}/${id}/process`, { method: 'POST', headers: authHeaders() })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || 'Processing failed')
  }
  return res.json()
}

export async function deleteStatement(id) {
  const res = await apiFetch(`${BASE}/${id}`, { method: 'DELETE', headers: authHeaders() })
  if (!res.ok) throw new Error('Delete failed')
}

export async function getTransactions(id) {
  const res = await apiFetch(`${BASE}/${id}/transactions`, { headers: authHeaders() })
  if (!res.ok) throw new Error('Failed to fetch transactions')
  return res.json()
}

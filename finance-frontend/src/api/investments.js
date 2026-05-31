import { apiFetch, authHeaders } from './client'

const BASE = 'http://localhost:8080/api/investments'

export async function getInvestments() {
  const res = await apiFetch(BASE, { headers: authHeaders() })
  return res.json()
}

export async function createInvestment(data) {
  const res = await apiFetch(BASE, {
    method: 'POST',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
  return res.json()
}

export async function updateInvestment(id, data) {
  const res = await apiFetch(`${BASE}/${id}`, {
    method: 'PUT',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
  return res.json()
}

export async function deleteInvestment(id) {
  await apiFetch(`${BASE}/${id}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
}

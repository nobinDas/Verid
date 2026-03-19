const BASE = 'http://localhost:8080/api/analytics'

function authHeaders() {
  return { Authorization: `Bearer ${localStorage.getItem('token')}` }
}

export async function getCategories(from, to) {
  const res = await fetch(`${BASE}/categories?from=${from}&to=${to}`, {
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error('Failed to fetch categories')
  return res.json()
}

export async function getTopTransactions(from, to) {
  const res = await fetch(`${BASE}/top-transactions?from=${from}&to=${to}`, {
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error('Failed to fetch top transactions')
  return res.json()
}

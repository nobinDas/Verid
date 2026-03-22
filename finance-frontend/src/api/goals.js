import { API_BASE } from './client'
const BASE = `${API_BASE}/api/goals`

function authHeaders() {
  const token = localStorage.getItem('token')
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
}

export async function getGoals() {
  const res = await fetch(BASE, { headers: authHeaders() })
  if (!res.ok) throw new Error('Failed to fetch goals')
  return res.json()
}

export async function getGoal(id) {
  const res = await fetch(`${BASE}/${id}`, { headers: authHeaders() })
  if (!res.ok) throw new Error('Failed to fetch goal')
  return res.json()
}

export async function getGoalsSavingsSummary() {
  const res = await fetch(`${BASE}/savings-summary`, { headers: authHeaders() })
  if (!res.ok) throw new Error('Failed to fetch savings summary')
  return res.json()
}

export async function createGoal(data) {
  const res = await fetch(BASE, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error('Failed to create goal')
  return res.json()
}

export async function updateGoal(id, data) {
  const res = await fetch(`${BASE}/${id}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error('Failed to update goal')
  return res.json()
}

export async function depositToGoal(id, amount) {
  const res = await fetch(`${BASE}/${id}/deposit`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ amount }),
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) throw new Error(data.message || 'Deposit failed')
  return data
}

export async function deleteGoal(id) {
  const res = await fetch(`${BASE}/${id}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error('Failed to delete goal')
}

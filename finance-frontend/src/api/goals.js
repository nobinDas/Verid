const BASE = 'http://localhost:8080/api/goals'

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

export async function deleteGoal(id) {
  const res = await fetch(`${BASE}/${id}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error('Failed to delete goal')
}

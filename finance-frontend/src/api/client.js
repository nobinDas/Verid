export function authHeaders() {
  return { Authorization: `Bearer ${localStorage.getItem('token')}` }
}

export async function apiFetch(url, options = {}) {
  const res = await fetch(url, options)
  if (res.status === 401 || res.status === 403) {
    localStorage.removeItem('token')
    window.location.href = '/login'
    return
  }
  return res
}

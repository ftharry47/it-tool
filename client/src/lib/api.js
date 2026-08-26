const API_BASE = '/api'

export async function callAPI(fn, ...args) {
  const res = await fetch(`${API_BASE}/${fn}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ args })
  })
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.json()
}

import { auth } from './auth'
import router from './router'

async function request(method, path, body, params) {
  const headers = { 'Content-Type': 'application/json' }
  if (auth.token) {
    headers['Authorization'] = `Bearer ${auth.token}`
  }
  
  let url = `/api${path}`
  if (params) {
    const qs = new URLSearchParams(
      Object.entries(params).filter(([, v]) => v !== undefined && v !== null)
    ).toString()
    if (qs) url += `?${qs}`
  }

  const res = await fetch(url, {
    method,
    headers,
    body: body != null ? JSON.stringify(body) : undefined,
  })

  if (res.status === 401) {
    auth.logout()
    router.push('/login')
    throw new Error('Unauthorized')
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({ detail: res.statusText }))
    throw Object.assign(new Error(err.detail || res.statusText), { status: res.status, body: err })
  }

  const text = await res.text()
  return text ? JSON.parse(text) : null
}

export const api = {
  get: (path, opts) => request('GET', path, undefined, opts?.params),
  post: (path, body) => request('POST', path, body),
  put: (path, body) => request('PUT', path, body),
  delete: (path) => request('DELETE', path),
}

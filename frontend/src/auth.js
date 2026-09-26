import { reactive } from 'vue'

const _stored = JSON.parse(localStorage.getItem('auth') || '{}')

export const auth = reactive({
  token: _stored.token || null,
  userId: _stored.userId || null,
  username: _stored.username || null,

  get isLoggedIn() {
    return !!this.token
  },

  login(token, userId, username) {
    this.token = token
    this.userId = userId
    this.username = username
    localStorage.setItem('auth', JSON.stringify({ token, userId, username }))
  },

  logout() {
    this.token = null
    this.userId = null
    this.username = null
    localStorage.removeItem('auth')
  },
})

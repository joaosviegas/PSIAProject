<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '@/auth'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const res = await fetch('/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value, password: password.value }),
    })
    const data = await res.json().catch(() => ({ detail: res.statusText }))
    if (!res.ok) {
      error.value = data.detail || 'Registration failed'
      return
    }

    // Registration succeeded: log the new user in straight away.
    const loginRes = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value, password: password.value }),
    })
    const loginData = await loginRes.json().catch(() => ({}))
    if (!loginRes.ok) {
      // Account exists but auto-login failed; fall back to the login page.
      router.push('/login')
      return
    }
    auth.login(loginData.token, loginData.userId, loginData.username)
    router.push('/')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <h1>Register</h1>
    <form @submit.prevent="submit">
      <label>Username <input v-model="username" required minlength="3" maxlength="30" pattern="[a-zA-Z0-9_]+" /></label>
      <label>Password <input v-model="password" type="password" required minlength="8" maxlength="100" /></label>
      <p v-if="error" class="error">{{ error }}</p>
      <button :disabled="loading">{{ loading ? 'Registering...' : 'Register' }}</button>
    </form>
    <p>Already have an account? <router-link to="/login">Login</router-link></p>
  </div>
</template>
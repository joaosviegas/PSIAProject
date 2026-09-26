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
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value, password: password.value }),
    })
    const data = await res.json().catch(() => ({ detail: res.statusText }))
    if (!res.ok) {
      error.value = data.detail || 'Login failed'
      return
    }
    auth.login(data.token, data.userId, data.username)
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
    <h1>Login</h1>
    <form @submit.prevent="submit">
      <label>Username <input v-model="username" required /></label>
      <label>Password <input v-model="password" type="password" required /></label>
      <p v-if="error" class="error">{{ error }}</p>
      <button :disabled="loading">{{ loading ? 'Logging in...' : 'Login' }}</button>
    </form>
    <p>No account? <router-link to="/register">Register</router-link></p>
  </div>
</template>

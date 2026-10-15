<script setup>
import { watch } from 'vue'
import { auth } from '@/auth'
import { api } from '@/api'
import { useRouter } from 'vue-router'

const router = useRouter()

// Reading the profile creates an empty one on first call, so every account shows up in People
// even if its owner never opened the Profile page.
watch(() => auth.isLoggedIn, (loggedIn) => {
  if (loggedIn) api.get('/profiles/me').catch(() => {})
}, { immediate: true })

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <header class="site-header">
    <nav>
      <router-link to="/" class="logo">Letter<span>slop</span>d</router-link>
      <div v-if="auth.isLoggedIn" class="nav-links">
        <router-link to="/">Feed</router-link>
        <router-link to="/ask">Ask AI</router-link>
        <router-link to="/movies">Movies</router-link>
        <router-link to="/profile">Profile</router-link>
        <router-link to="/people">People</router-link>
        <button @click="logout">Logout ({{ auth.username }})</button>
      </div>
      <div v-else class="nav-links">
        <router-link to="/login">Login</router-link>
        <router-link to="/register">Register</router-link>
      </div>
    </nav>
  </header>
  <main>
    <router-view />
  </main>
</template>

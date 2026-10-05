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
  <nav v-if="auth.isLoggedIn">
    <router-link to="/">Feed</router-link> |
    <router-link to="/ask">Ask AI</router-link> |
    <router-link to="/movies">Movies</router-link> |
    <router-link to="/profile">Profile</router-link> |
    <router-link to="/people">People</router-link> |
    <button @click="logout">Logout ({{ auth.username }})</button>
  </nav>
  <nav v-else>
    <router-link to="/login">Login</router-link> |
    <router-link to="/register">Register</router-link>
  </nav>
  <main>
    <router-view />
  </main>
</template>

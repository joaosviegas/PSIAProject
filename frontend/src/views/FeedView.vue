<script setup>
import { ref, onMounted } from 'vue'
import { api } from '@/api'

const username = ref('')
const error = ref('')

onMounted(async () => {
  try {
    const data = await api.get('/auth/me')
    username.value = data.username
  } catch (e) {
    error.value = e.message
  }
})
</script>

<template>
  <div>
    <h1>Feed</h1>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="username">Logged in as <strong>{{ username }}</strong></p>
    <p v-else>Loading...</p>
    <p><em>Feed coming soon.</em></p>
  </div>
</template>

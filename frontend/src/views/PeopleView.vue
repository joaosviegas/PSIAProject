<script setup>
import { ref, onMounted, watch } from 'vue'
import { api } from '../api'
import { useRouter } from 'vue-router'

const router = useRouter()
const query = ref('')
const genre = ref('')
const genres = ref([])
const people = ref([])
const page = ref(0)
const totalPages = ref(0)
const loading = ref(false)

async function loadGenres() {
  try { genres.value = await api.get('/movies/genres') || [] } catch (e) { /* ignore */ }
}

async function search() {
  loading.value = true
  try {
    const data = await api.get('/profiles', {
      params: {
        q: query.value || undefined,
        genre: genre.value || undefined,
        page: page.value,
        size: 20,
      },
    })
    people.value = data?.items || []
    totalPages.value = data?.totalPages || 0
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

watch([query, genre], () => { page.value = 0; search() })

onMounted(() => { loadGenres(); search() })
</script>

<template>
  <div class="people-view">
    <h1>Browse people</h1>

    <div class="filters">
      <input v-model="query" type="text" placeholder="Search by name..." class="search-input" />
      <select v-model="genre">
        <option value="">All genres</option>
        <option v-for="g in genres" :key="g" :value="g">{{ g }}</option>
      </select>
    </div>

    <p v-if="loading">Loading...</p>
    <p v-else-if="people.length === 0">No users found.</p>

    <div v-else class="people-grid">
      <div v-for="p in people" :key="p.userId" class="person-card" @click="router.push(`/people/${p.userId}`)">
        <h3>{{ p.displayName }}</h3>
        <p class="username">@{{ p.username }}</p>
        <p v-if="p.favoriteGenres?.length" class="genres">{{ p.favoriteGenres.join(', ') }}</p>
      </div>
    </div>

    <div class="pagination" v-if="totalPages > 1">
      <button :disabled="page === 0" @click="page--; search()">Previous</button>
      <span>Page {{ page + 1 }} of {{ totalPages }}</span>
      <button :disabled="page + 1 >= totalPages" @click="page++; search()">Next</button>
    </div>
  </div>
</template>

<style scoped>
.filters { display: flex; flex-wrap: wrap; gap: 0.75rem; margin-bottom: 1.5rem; }
.search-input { flex: 1; min-width: 220px; }
.people-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 1rem; }
.person-card {
  padding: 1.25rem;
  border-radius: var(--radius);
  background: var(--bg-2);
  border: 1px solid var(--border);
  cursor: pointer;
  transition: border-color 0.2s, transform 0.2s;
}
.person-card:hover { border-color: var(--primary); transform: translateY(-3px); }
.person-card h3 { margin: 0 0 0.2rem; font-size: 1.05rem; }
.username { color: var(--text-muted); font-size: 0.85rem; margin: 0; }
.genres { font-size: 0.8rem; color: var(--accent); margin: 0.6rem 0 0; }
</style>

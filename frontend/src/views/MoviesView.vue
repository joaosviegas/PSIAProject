<script setup>
import { ref, onMounted, watch } from 'vue'
import { api } from '../api'

const query = ref('')
const genre = ref('')
const genres = ref([])
const movies = ref([])
const page = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const error = ref('')

async function loadGenres() {
  try {
    const data = await api.get('/movies/genres')
    genres.value = data || []
  } catch (e) {
    console.error('Failed to load genres', e)
  }
}

async function search() {
  loading.value = true
  error.value = ''
  try {
    const data = await api.get('/movies', {
      params: {
        query: query.value || undefined,
        genre: genre.value || undefined,
        page: page.value,
        size: 20,
      },
    })
    movies.value = data?.content || []
    totalPages.value = data?.totalPages || 0
  } catch (e) {
    error.value = 'Could not load movies. Please try again.'
    console.error(e)
  } finally {
    loading.value = false
  }
}

function nextPage() {
  if (page.value + 1 < totalPages.value) {
    page.value += 1
    search()
  }
}

function prevPage() {
  if (page.value > 0) {
    page.value -= 1
    search()
  }
}

watch([query, genre], () => {
  page.value = 0
  search()
})

onMounted(() => {
  loadGenres()
  search()
})
</script>

<template>
  <div class="movies-view">
    <h1>Browse movies</h1>

    <div class="filters">
      <input
        v-model="query"
        type="text"
        placeholder="Search by title..."
        class="search-input"
      />
      <select v-model="genre" class="genre-select">
        <option value="">All genres</option>
        <option v-for="g in genres" :key="g" :value="g">{{ g }}</option>
      </select>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">Loading...</p>
    <p v-else-if="movies.length === 0">No movies found.</p>

    <div v-else class="movie-grid">
      <div v-for="m in movies" :key="m.id" class="movie-card">
        <h3>{{ m.title }} <span v-if="m.startYear">({{ m.startYear }})</span></h3>
        <p class="genres">{{ m.genres.join(', ') }}</p>
        <p v-if="m.avgRating" class="rating">★ {{ m.avgRating }} ({{ m.numVotes }} votes)</p>
      </div>
    </div>

    <div class="pagination" v-if="totalPages > 1">
      <button :disabled="page === 0" @click="prevPage">Previous</button>
      <span>Page {{ page + 1 }} of {{ totalPages }}</span>
      <button :disabled="page + 1 >= totalPages" @click="nextPage">Next</button>
    </div>
  </div>
</template>

<style scoped>
.movies-view {
  max-width: 960px;
  margin: 0 auto;
  padding: 1.5rem;
}
.filters {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 1.5rem;
}
.search-input {
  flex: 1;
  padding: 0.5rem;
}
.genre-select {
  padding: 0.5rem;
}
.movie-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 1rem;
}
.movie-card {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1rem;
}
.movie-card h3 {
  margin: 0 0 0.5rem;
  font-size: 1rem;
}
.genres {
  font-size: 0.85rem;
  color: #666;
}
.rating {
  font-size: 0.85rem;
  margin-top: 0.5rem;
}
.pagination {
  display: flex;
  gap: 1rem;
  align-items: center;
  justify-content: center;
  margin-top: 1.5rem;
}
.error {
  color: #b00020;
}
</style>
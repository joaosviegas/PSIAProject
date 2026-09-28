<script setup>
import { ref, onMounted, watch } from 'vue'
import { api } from '../api'

const query = ref('')
const genre = ref('')
const yearFrom = ref('')
const yearTo = ref('')
const minRating = ref('')
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
        yearFrom: yearFrom.value || undefined,
        yearTo: yearTo.value || undefined,
        minRating: minRating.value || undefined,
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

watch([query, genre, yearFrom, yearTo, minRating], () => {
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
        placeholder="Search title, director, actor..."
        class="search-input"
      />
      <select v-model="genre" class="filter-select">
        <option value="">All genres</option>
        <option v-for="g in genres" :key="g" :value="g">{{ g }}</option>
      </select>
    </div>
    <div class="filters">
      <input v-model="yearFrom" type="number" placeholder="Year from" min="1900" max="2030" class="filter-small" />
      <input v-model="yearTo" type="number" placeholder="Year to" min="1900" max="2030" class="filter-small" />
      <select v-model="minRating" class="filter-select">
        <option value="">Any rating</option>
        <option value="9">★ 9+</option>
        <option value="8">★ 8+</option>
        <option value="7">★ 7+</option>
        <option value="6">★ 6+</option>
        <option value="5">★ 5+</option>
      </select>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">Loading...</p>
    <p v-else-if="movies.length === 0">No movies found.</p>

    <div v-else class="movie-grid">
      <div v-for="m in movies" :key="m.id" class="movie-card">
        <img v-if="m.posterUrl" :src="m.posterUrl" :alt="m.title" class="poster" />
        <div class="movie-info">
          <h3>{{ m.title }} <span v-if="m.startYear">({{ m.startYear }})</span></h3>
          <p class="genres">{{ m.genres.join(', ') }}</p>
          <p v-if="m.avgRating" class="rating">★ {{ m.avgRating }} ({{ m.numVotes }} votes)</p>
          <p v-if="m.directors" class="meta">Dir: {{ m.directors }}</p>
          <p v-if="m.overview" class="overview">{{ m.overview }}</p>
        </div>
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
  margin-bottom: 0.75rem;
}
.search-input {
  flex: 1;
  padding: 0.5rem;
}
.filter-select {
  padding: 0.5rem;
}
.filter-small {
  width: 110px;
  padding: 0.5rem;
}
.movie-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
  margin-top: 0.75rem;
}
.movie-card {
  display: flex;
  gap: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 0.75rem;
}
.poster {
  width: 80px;
  height: 120px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
}
.movie-info {
  min-width: 0;
}
.movie-card h3 {
  margin: 0 0 0.35rem;
  font-size: 1rem;
}
.genres {
  font-size: 0.85rem;
  color: #666;
}
.rating {
  font-size: 0.85rem;
  margin-top: 0.25rem;
}
.meta {
  font-size: 0.8rem;
  color: #888;
  margin-top: 0.25rem;
}
.overview {
  font-size: 0.8rem;
  color: #444;
  margin-top: 0.35rem;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  line-clamp: 3;
  overflow: hidden;
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

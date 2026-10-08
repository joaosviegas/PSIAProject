<script setup>
import { ref, onMounted, watch } from 'vue'
import { api } from '../api'
import { useLikeDislike } from '../composables/useLikeDislike'
import MovieCard from '../components/MovieCard.vue'

const { likedIds, dislikedIds, loadProfile, toggleLike, toggleDislike } = useLikeDislike()

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
  loadProfile()
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
      <MovieCard v-for="m in movies" :key="m.id" :movie="m"
        :note="m.directors ? `Dir: ${m.directors}` : ''"
        :liked="likedIds.has(m.id)" :disliked="dislikedIds.has(m.id)"
        @like="toggleLike(m.id)" @dislike="toggleDislike(m.id)" />
    </div>

    <div class="pagination" v-if="totalPages > 1">
      <button :disabled="page === 0" @click="prevPage">Previous</button>
      <span>Page {{ page + 1 }} of {{ totalPages }}</span>
      <button :disabled="page + 1 >= totalPages" @click="nextPage">Next</button>
    </div>
  </div>
</template>

<style scoped>
.filters { display: flex; flex-wrap: wrap; gap: 0.75rem; margin-bottom: 0.75rem; }
.search-input { flex: 1; min-width: 220px; }
.filter-small { width: 130px; }
.movie-grid { margin-top: 1.5rem; }
</style>

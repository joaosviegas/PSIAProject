<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'

const MOODS = [
  'Something uplifting', 'Something intense', 'Something to relax to',
  'Something thought-provoking', 'Something scary', 'Something funny',
]

const genres = ref([])
const selectedGenres = ref([])
const mood = ref('')
const notes = ref('')

const loading = ref(false)
const error = ref('')
const result = ref(null)

async function loadGenres() {
  try {
    const data = await api.get('/movies/genres')
    genres.value = data || []
  } catch (e) {
    console.error('Failed to load genres', e)
  }
}

function toggleGenre(g) {
  const idx = selectedGenres.value.indexOf(g)
  if (idx === -1) {
    selectedGenres.value.push(g)
  } else {
    selectedGenres.value.splice(idx, 1)
  }
}

async function submit() {
  if (selectedGenres.value.length === 0 && !mood.value && !notes.value.trim()) {
    error.value = 'Pick at least one genre, a mood, or add a note.'
    return
  }

  loading.value = true
  error.value = ''
  result.value = null
  try {
    const data = await api.post('/recommendations', {
      genres: selectedGenres.value,
      mood: mood.value,
      notes: notes.value.trim() || null,
    })
    result.value = data
  } catch (e) {
    error.value = 'Could not get recommendations right now. Please try again.'
    console.error(e)
  } finally {
    loading.value = false
  }
}

onMounted(loadGenres)
</script>

<template>
  <div class="ask-view">
    <h1>Ask AI for recommendations</h1>
    <p class="subtitle">Tell us what you're in the mood for and we'll ask the AI to pick some movies for you.</p>

    <form @submit.prevent="submit" class="form">
      <fieldset>
        <legend>Genres (optional, pick any number)</legend>
        <div class="genre-chips">
          <button
            v-for="g in genres"
            :key="g"
            type="button"
            class="chip"
            :class="{ active: selectedGenres.includes(g) }"
            @click="toggleGenre(g)"
          >
            {{ g }}
          </button>
        </div>
      </fieldset>

      <label class="field">
        Mood
        <select v-model="mood">
          <option value="">No particular mood</option>
          <option v-for="m in MOODS" :key="m" :value="m">{{ m }}</option>
        </select>
      </label>

      <label class="field">
        Anything else? (optional)
        <textarea
          v-model="notes"
          maxlength="500"
          rows="3"
          placeholder="e.g. nothing too long, I loved Inception..."
        />
      </label>

      <p v-if="error" class="error">{{ error }}</p>

      <button type="submit" :disabled="loading" class="submit-btn">
        {{ loading ? 'Thinking...' : 'Get recommendations' }}
      </button>
    </form>

    <section v-if="result" class="result">
      <p class="summary">
        {{ result.summary }}
        <span v-if="!result.aiGenerated" class="fallback-note">(fallback ranking, not AI-generated)</span>
      </p>
      <div class="movie-grid">
        <div v-for="m in result.movies" :key="m.id" class="movie-card">
          <h3>{{ m.title }} <span v-if="m.startYear">({{ m.startYear }})</span></h3>
          <p class="genres">{{ m.genres.join(', ') }}</p>
          <p v-if="m.avgRating" class="rating">★ {{ m.avgRating }}</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.ask-view {
  max-width: 720px;
  margin: 0 auto;
  padding: 1.5rem;
}
.subtitle {
  color: #666;
  margin-bottom: 1.5rem;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
fieldset {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1rem;
}
.genre-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.chip {
  border: 1px solid #ccc;
  border-radius: 999px;
  padding: 0.35rem 0.9rem;
  background: white;
  cursor: pointer;
}
.chip.active {
  background: #333;
  color: white;
  border-color: #333;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.submit-btn {
  padding: 0.6rem 1.2rem;
  align-self: flex-start;
}
.error {
  color: #b00020;
}
.result {
  margin-top: 2rem;
  padding-top: 1.5rem;
  border-top: 1px solid #ddd;
}
.summary {
  margin-bottom: 1rem;
}
.fallback-note {
  color: #999;
  font-size: 0.85rem;
}
.movie-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
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
</style>
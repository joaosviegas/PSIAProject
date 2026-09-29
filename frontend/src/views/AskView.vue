<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import { useLikeDislike } from '../composables/useLikeDislike'

const { likedIds, dislikedIds, loadProfile, toggleLike, toggleDislike } = useLikeDislike()

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

onMounted(() => { loadGenres(); loadProfile() })
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
          <img v-if="m.posterUrl" :src="m.posterUrl" :alt="m.title" class="poster" />
          <div class="movie-info">
            <h3>{{ m.title }} <span v-if="m.startYear">({{ m.startYear }})</span></h3>
            <p class="genres">{{ m.genres.join(', ') }}</p>
            <p v-if="m.avgRating" class="rating">★ {{ m.avgRating }}</p>
            <p v-if="m.overview" class="overview">{{ m.overview }}</p>
            <div class="actions">
              <button
                :class="['btn-like', { active: likedIds.has(m.id) }]"
                @click="toggleLike(m.id)"
                :title="likedIds.has(m.id) ? 'Remove like' : 'Like'"
              >&#9829;</button>
              <button
                :class="['btn-dislike', { active: dislikedIds.has(m.id) }]"
                @click="toggleDislike(m.id)"
                :title="dislikedIds.has(m.id) ? 'Remove dislike' : 'Dislike'"
              >&#10005;</button>
            </div>
          </div>
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
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
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
  margin: 0 0 0.5rem;
  font-size: 1rem;
}
.genres {
  font-size: 0.85rem;
  color: #666;
}
.overview {
  font-size: 0.8rem;
  color: #444;
  margin-top: 0.35rem;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.4rem;
}
.btn-like, .btn-dislike {
  border: 1px solid #ccc;
  border-radius: 4px;
  background: #f5f5f5;
  cursor: pointer;
  padding: 0.2rem 0.5rem;
  font-size: 1rem;
}
.btn-like.active {
  color: #e53935;
  border-color: #e53935;
  background: #fce4ec;
}
.btn-dislike.active {
  color: #757575;
  border-color: #757575;
  background: #eeeeee;
}
</style>

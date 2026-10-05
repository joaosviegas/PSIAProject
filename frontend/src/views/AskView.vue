<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import { useLikeDislike } from '../composables/useLikeDislike'
import MovieCard from '../components/MovieCard.vue'

const { likedIds, dislikedIds, profileData, loadProfile, toggleLike, toggleDislike } = useLikeDislike()

const MOODS = [
  'Something uplifting', 'Something intense', 'Something to relax to',
  'Something thought-provoking', 'Something scary', 'Something funny',
]

const genres = ref([])
const selectedGenres = ref([])
const mood = ref('')
const notes = ref('')

const loading = ref(false)   // waiting for the instant results
const refining = ref(false)  // instant results are on screen, the AI is still working
const error = ref('')
const result = ref(null)
let requestId = 0

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

// Two calls in parallel: /quick answers in milliseconds (taste-aware ranking, no LLM) and is shown
// right away; /recommendations is the same shortlist curated by the AI and replaces it when ready.
async function submit() {
  if (selectedGenres.value.length === 0 && !mood.value && !notes.value.trim()) {
    error.value = 'Pick at least one genre, a mood, or add a note.'
    return
  }

  const id = ++requestId
  const body = {
    genres: selectedGenres.value,
    mood: mood.value,
    notes: notes.value.trim() || null,
  }
  loading.value = true
  refining.value = true
  error.value = ''
  result.value = null

  const aiCall = api.post('/recommendations', body).then(data => ({ data }), err => ({ err }))

  try {
    const quick = await api.post('/recommendations/quick', body)
    if (id !== requestId) return
    result.value = quick
  } catch (e) {
    console.error(e)
  } finally {
    if (id === requestId) loading.value = false
  }

  const ai = await aiCall
  if (id !== requestId) return // a newer question replaced this one
  refining.value = false
  if (ai.data) {
    result.value = ai.data
  } else if (!result.value) {
    error.value = 'Could not get recommendations right now. Please try again.'
    console.error(ai.err)
  }
}

onMounted(async () => {
  loadGenres()
  await loadProfile()
  // Start from the categories saved in the profile; the user can change them for this question.
  if (selectedGenres.value.length === 0) selectedGenres.value = [...(profileData.value?.favoriteGenres || [])]
})
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
        {{ loading ? 'Finding matches...' : 'Get recommendations' }}
      </button>
    </form>

    <section v-if="result" class="result">
      <p class="summary">
        {{ result.summary }}
        <span v-if="!refining && !result.aiGenerated && result.movies.length" class="fallback-note">(ranked by your taste, not AI-curated)</span>
      </p>
      <p v-if="refining" class="refining">&#10024; The AI is refining these picks...</p>
      <div class="movie-grid">
        <MovieCard v-for="m in result.movies" :key="m.id" :movie="m"
          :liked="likedIds.has(m.id)" :disliked="dislikedIds.has(m.id)"
          @like="toggleLike(m.id)" @dislike="toggleDislike(m.id)" />
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
.refining {
  color: #1976d2;
  font-size: 0.9rem;
  margin-bottom: 0.75rem;
}
</style>

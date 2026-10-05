<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api } from '@/api'
import { auth } from '@/auth'
import { useLikeDislike } from '@/composables/useLikeDislike'
import MovieCard from '@/components/MovieCard.vue'

const POLL_MS = 3000
const MAX_POLLS = 40 // ~2 minutes, then stop waiting and keep the instant picks
const MAX_GENRES = 10

const { likedIds, dislikedIds, loadProfile, toggleLike, toggleDislike } = useLikeDislike()

// 'loading' | 'needs_preferences' | 'building' | 'ready'
const status = ref('loading')
const picks = ref([])
const discover = ref([])
const similar = ref([])
const summary = ref('')
const aiGenerated = ref(false)
const generatedAt = ref(null)
const error = ref('')
const gaveUpWaiting = ref(false)

const allGenres = ref([])
const chosen = ref([])
const saving = ref(false)
const shuffling = ref(false)

let pollTimer = null
let polls = 0

// The random section must never repeat something already shown above it.
const visibleDiscover = computed(() => {
  const shown = new Set([...picks.value.map(m => m.id), ...similar.value.map(p => p.movie.id)])
  return discover.value.filter(m => !shown.has(m.id))
})

function apply(data, { keepDiscover }) {
  status.value = data.status
  if (data.status === 'needs_preferences') {
    loadGenres()
    return
  }
  picks.value = data.picks || []
  similar.value = data.fromSimilarUsers || []
  summary.value = data.summary || ''
  aiGenerated.value = !!data.aiGenerated
  generatedAt.value = data.generatedAt || null
  // Polling must not reshuffle the random section under the user's eyes.
  if (!keepDiscover || discover.value.length === 0) discover.value = data.discover || []
}

async function loadFeed({ first = false } = {}) {
  clearTimeout(pollTimer)
  try {
    const data = await api.get('/feed')
    error.value = ''
    apply(data, { keepDiscover: !first })
    if (data.status === 'building') {
      if (polls++ < MAX_POLLS) pollTimer = setTimeout(() => loadFeed(), POLL_MS)
      else gaveUpWaiting.value = true
    }
  } catch (e) {
    if (status.value === 'loading') status.value = 'error'
    error.value = 'Could not load your feed right now. Please try again.'
    console.error(e)
  }
}

async function loadGenres() {
  if (allGenres.value.length) return
  try { allGenres.value = (await api.get('/movies/genres')) || [] } catch (e) { console.error(e) }
}

function toggleGenre(g) {
  const i = chosen.value.indexOf(g)
  if (i >= 0) chosen.value.splice(i, 1)
  else if (chosen.value.length < MAX_GENRES) chosen.value.push(g)
}

async function savePreferences() {
  if (!chosen.value.length) return
  saving.value = true
  error.value = ''
  try {
    let profile = null
    try { profile = await api.get('/profiles/me') } catch (e) { /* nothing saved yet */ }
    await api.put('/profiles/me', {
      displayName: profile?.displayName || auth.username,
      bio: profile?.bio || '',
      favoriteGenres: chosen.value,
      favoriteMovieIds: profile?.favoriteMovieIds || [],
      dislikedMovieIds: profile?.dislikedMovieIds || [],
    })
    api.post('/feed/refresh').catch(() => {}) // start the AI build right away
    polls = 0
    gaveUpWaiting.value = false
    status.value = 'loading'
    await loadFeed({ first: true })
    loadProfile() // pick up the new profile for the like buttons
  } catch (e) {
    error.value = 'Could not save your categories. Please try again.'
    console.error(e)
  } finally {
    saving.value = false
  }
}

async function shuffle() {
  shuffling.value = true
  try {
    discover.value = (await api.get('/feed/discover')) || []
  } catch (e) {
    console.error(e)
  } finally {
    shuffling.value = false
  }
}

onMounted(() => { loadFeed({ first: true }); loadProfile() })
onUnmounted(() => clearTimeout(pollTimer))
</script>

<template>
  <div class="feed-view">
    <h1>Your feed</h1>

    <p v-if="status === 'loading'">Loading...</p>
    <div v-else-if="status === 'error'">
      <p class="error">{{ error }}</p>
      <button @click="loadFeed({ first: true })">Try again</button>
    </div>

    <!-- New account / no categories yet: nothing to suggest, so ask. -->
    <section v-else-if="status === 'needs_preferences'" class="onboarding">
      <h2>What do you like to watch?</h2>
      <p class="hint">Pick up to {{ MAX_GENRES }} categories and we'll build your feed right away.</p>
      <div class="genre-chips">
        <button v-for="g in allGenres" :key="g" type="button"
          :class="['chip', { active: chosen.includes(g) }]" @click="toggleGenre(g)">{{ g }}</button>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <button class="primary" :disabled="!chosen.length || saving" @click="savePreferences">
        {{ saving ? 'Building your feed...' : `Build my feed (${chosen.length})` }}
      </button>
    </section>

    <template v-else>
      <p v-if="status === 'building'" class="refining">
        <template v-if="!gaveUpWaiting">&#10024; The AI is fine-tuning your picks, these are instant picks based on your taste.</template>
        <template v-else>The AI is taking a while, so you're seeing instant picks based on your taste. Reload later for the AI version.</template>
      </p>
      <p v-else-if="generatedAt" class="generated">Updated {{ new Date(generatedAt).toLocaleString() }}</p>
      <p class="hint">
        Based on: your categories and likes.
        <router-link to="/profile">Change categories</router-link>
      </p>

      <section>
        <h2>{{ aiGenerated ? 'AI picks' : 'Smart picks' }}</h2>
        <p v-if="summary" class="summary">
          {{ summary }}
          <span v-if="status === 'ready' && !aiGenerated" class="fallback-note">(AI was busy, ranked by your taste instead)</span>
        </p>
        <p v-if="!picks.length" class="empty">Nothing new matches your categories yet — add more in your profile.</p>
        <div class="movie-grid">
          <MovieCard v-for="m in picks" :key="m.id" :movie="m"
            :liked="likedIds.has(m.id)" :disliked="dislikedIds.has(m.id)"
            @like="toggleLike(m.id)" @dislike="toggleDislike(m.id)" />
        </div>
      </section>

      <section v-if="visibleDiscover.length">
        <div class="section-head">
          <h2>Discover from your categories</h2>
          <button :disabled="shuffling" @click="shuffle">{{ shuffling ? '...' : 'Shuffle' }}</button>
        </div>
        <div class="movie-grid">
          <MovieCard v-for="m in visibleDiscover" :key="m.id" :movie="m"
            :liked="likedIds.has(m.id)" :disliked="dislikedIds.has(m.id)"
            @like="toggleLike(m.id)" @dislike="toggleDislike(m.id)" />
        </div>
      </section>

      <section v-if="similar.length">
        <h2>Liked by people like you</h2>
        <div class="movie-grid">
          <MovieCard v-for="p in similar" :key="p.movie.id" :movie="p.movie"
            :note="`Liked by ${p.likedBy.join(', ')}`"
            :liked="likedIds.has(p.movie.id)" :disliked="dislikedIds.has(p.movie.id)"
            @like="toggleLike(p.movie.id)" @dislike="toggleDislike(p.movie.id)" />
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.feed-view { max-width: 720px; margin: 0 auto; padding: 1.5rem; }
section { margin-top: 1.5rem; }
.section-head { display: flex; align-items: center; justify-content: space-between; }
.generated { color: #666; font-size: 0.85rem; }
.refining { color: #1976d2; font-size: 0.9rem; }
.hint { color: #666; font-size: 0.85rem; }
.empty { color: #999; font-style: italic; }
.error { color: #b00020; }
.summary { margin-bottom: 1rem; }
.fallback-note { color: #999; font-size: 0.85rem; }
.genre-chips { display: flex; flex-wrap: wrap; gap: 0.5rem; margin: 1rem 0; }
.chip { border: 1px solid #ccc; border-radius: 999px; padding: 0.35rem 0.9rem; background: white; cursor: pointer; }
.chip.active { background: #1976d2; color: white; border-color: #1976d2; }
.primary { padding: 0.6rem 1.2rem; }
.movie-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 1rem; }
</style>

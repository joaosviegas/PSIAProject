<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import { auth } from '../auth'

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')
const isNew = ref(false)

const displayName = ref('')
const bio = ref('')
const selectedGenres = ref([])
const allGenres = ref([])
const likedMovies = ref([])
const dislikedMovies = ref([])
const likedIds = ref([])
const dislikedIds = ref([])

async function loadProfile() {
  loading.value = true
  error.value = ''
  try {
    const profile = await api.get('/profiles/me')
    displayName.value = profile.displayName
    bio.value = profile.bio || ''
    selectedGenres.value = profile.favoriteGenres || []
    likedIds.value = profile.favoriteMovieIds || []
    dislikedIds.value = profile.dislikedMovieIds || []
    isNew.value = false
  } catch (e) {
    if (e.status === 404) {
      displayName.value = auth.username
      isNew.value = true
    } else {
      error.value = e.message
    }
  }
  loading.value = false
}

async function loadGenres() {
  try {
    allGenres.value = await api.get('/movies/genres') || []
  } catch (e) { /* ignore */ }
}

async function loadMovieDetails() {
  const ids = [...new Set([...likedIds.value, ...dislikedIds.value])]
  if (ids.length === 0) { likedMovies.value = []; dislikedMovies.value = []; return }
  const results = await Promise.allSettled(ids.map(id => api.get(`/movies/${id}`)))
  const movieMap = {}
  results.forEach((r, i) => {
    if (r.status === 'fulfilled') movieMap[ids[i]] = r.value
  })
  likedMovies.value = likedIds.value.map(id => movieMap[id]).filter(Boolean)
  dislikedMovies.value = dislikedIds.value.map(id => movieMap[id]).filter(Boolean)
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    await api.put('/profiles/me', {
      displayName: displayName.value,
      bio: bio.value,
      favoriteGenres: selectedGenres.value,
      favoriteMovieIds: likedIds.value,
      dislikedMovieIds: dislikedIds.value,
    })
    isNew.value = false
    api.post('/feed/refresh').catch(() => {}) // rebuild the feed now so it is ready when you get there
    success.value = 'Profile saved!'
    setTimeout(() => success.value = '', 3000)
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

function removeLiked(id) {
  likedIds.value = likedIds.value.filter(i => i !== id)
  likedMovies.value = likedMovies.value.filter(m => m.id !== id)
}

function removeDisliked(id) {
  dislikedIds.value = dislikedIds.value.filter(i => i !== id)
  dislikedMovies.value = dislikedMovies.value.filter(m => m.id !== id)
}

function toggleGenre(g) {
  const idx = selectedGenres.value.indexOf(g)
  if (idx >= 0) selectedGenres.value.splice(idx, 1)
  else if (selectedGenres.value.length < 10) selectedGenres.value.push(g)
}

onMounted(async () => {
  await Promise.all([loadProfile(), loadGenres()])
  await loadMovieDetails()
})
</script>

<template>
  <div class="profile-view">
    <h1>{{ isNew ? 'Create your profile' : 'Edit profile' }}</h1>

    <p v-if="loading">Loading...</p>
    <form v-else @submit.prevent="save">
      <label>
        Display name
        <input v-model="displayName" maxlength="50" required />
      </label>

      <label>
        Bio
        <textarea v-model="bio" maxlength="500" rows="3"></textarea>
      </label>

      <fieldset>
        <legend>Favourite genres ({{ selectedGenres.length }}/10)</legend>
        <div class="genre-chips">
          <button
            v-for="g in allGenres" :key="g"
            type="button"
            :class="['chip', { active: selectedGenres.includes(g) }]"
            @click="toggleGenre(g)"
          >{{ g }}</button>
        </div>
      </fieldset>

      <div v-if="likedMovies.length" class="movie-list">
        <h3>Liked movies</h3>
        <div v-for="m in likedMovies" :key="m.id" class="movie-card">
          <img v-if="m.posterUrl" :src="m.posterUrl" :alt="m.title" class="poster" />
          <div class="movie-info">
            <h4>{{ m.title }} <span v-if="m.startYear || m.year">({{ m.startYear || m.year }})</span></h4>
            <p v-if="m.genres" class="genres">{{ m.genres.join(', ') }}</p>
            <p v-if="m.avgRating" class="rating">&#9733; {{ m.avgRating }}</p>
            <button type="button" class="remove-btn" @click="removeLiked(m.id)">Remove</button>
          </div>
        </div>
      </div>

      <div v-if="dislikedMovies.length" class="movie-list">
        <h3>Disliked movies</h3>
        <div v-for="m in dislikedMovies" :key="m.id" class="movie-card">
          <img v-if="m.posterUrl" :src="m.posterUrl" :alt="m.title" class="poster" />
          <div class="movie-info">
            <h4>{{ m.title }} <span v-if="m.startYear || m.year">({{ m.startYear || m.year }})</span></h4>
            <p v-if="m.genres" class="genres">{{ m.genres.join(', ') }}</p>
            <button type="button" class="remove-btn" @click="removeDisliked(m.id)">Remove</button>
          </div>
        </div>
      </div>

      <p class="hint">Like or dislike movies from the <router-link to="/movies">Movies</router-link> page.</p>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="success" class="success">{{ success }}</p>

      <button type="submit" :disabled="saving">{{ saving ? 'Saving...' : 'Save profile' }}</button>
    </form>
  </div>
</template>

<style scoped>
.profile-view { max-width: 760px; margin-inline: auto; }
form { display: flex; flex-direction: column; gap: 1.25rem; }
label { display: flex; flex-direction: column; gap: 0.4rem; color: var(--text-muted); font-size: 0.9rem; }
.movie-list h3 { margin: 0.5rem 0 0.75rem; }
.remove-btn { font-size: 0.75rem; padding: 0.3rem 0.8rem; margin-top: 0.35rem; }
.remove-btn:hover:not(:disabled) { background: var(--danger); }
button[type="submit"] { align-self: flex-start; padding: 0.65rem 1.6rem; }

.genre-chips { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.movie-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.6rem;
  margin-bottom: 0.6rem;
  border-radius: var(--radius-sm);
  background: var(--bg-2);
  border: 1px solid var(--border);
  transition: border-color 0.2s;
}
.movie-card:hover { border-color: var(--primary); }
.poster { width: 56px; height: 84px; object-fit: cover; border-radius: 6px; flex-shrink: 0; }
.movie-info { min-width: 0; }
.movie-info h4 { margin: 0 0 0.2rem; font-size: 0.95rem; font-weight: 500; }
.genres { font-size: 0.8rem; color: var(--text-muted); margin: 0; }
.rating { font-size: 0.8rem; color: var(--yellow); margin: 0.2rem 0; }
.hint { font-size: 0.85rem; color: var(--text-muted); }
</style>

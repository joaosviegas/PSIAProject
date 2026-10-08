<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import { useLikeDislike } from '../composables/useLikeDislike'

const route = useRoute()
const { likedIds, dislikedIds, loadProfile, toggleLike, toggleDislike } = useLikeDislike()

const profile = ref(null)
const likedMovies = ref([])
const dislikedMovies = ref([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  try {
    profile.value = await api.get(`/profiles/${route.params.userId}`)
    const ids = [...new Set([
      ...(profile.value.favoriteMovieIds || []),
      ...(profile.value.dislikedMovieIds || []),
    ])]
    if (ids.length) {
      const results = await Promise.allSettled(ids.map(id => api.get(`/movies/${id}`)))
      const map = {}
      results.forEach((r, i) => { if (r.status === 'fulfilled') map[ids[i]] = r.value })
      likedMovies.value = (profile.value.favoriteMovieIds || []).map(id => map[id]).filter(Boolean)
      dislikedMovies.value = (profile.value.dislikedMovieIds || []).map(id => map[id]).filter(Boolean)
    }
  } catch (e) {
    error.value = e.status === 404 ? 'Profile not found.' : e.message
  } finally {
    loading.value = false
  }
}

onMounted(() => { load(); loadProfile() })
</script>

<template>
  <div class="person-view">
    <p v-if="loading">Loading...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <template v-else>
      <h1>{{ profile.displayName }}</h1>
      <p class="username">@{{ profile.username }}</p>
      <p v-if="profile.bio" class="bio">{{ profile.bio }}</p>

      <div v-if="profile.favoriteGenres?.length">
        <h3>Favourite genres</h3>
        <div class="genre-chips">
          <span v-for="g in profile.favoriteGenres" :key="g" class="chip">{{ g }}</span>
        </div>
      </div>

      <div>
        <h3>Liked movies</h3>
        <p v-if="!likedMovies.length" class="empty">No liked movies yet.</p>
        <div v-for="m in likedMovies" :key="m.id" class="movie-card">
          <img v-if="m.posterUrl" :src="m.posterUrl" :alt="m.title" class="poster" />
          <div class="movie-info">
            <h4>{{ m.title }} <span v-if="m.startYear || m.year">({{ m.startYear || m.year }})</span></h4>
            <p v-if="m.genres" class="genres">{{ m.genres.join(', ') }}</p>
            <p v-if="m.avgRating" class="rating">&#9733; {{ m.avgRating }}</p>
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

      <div v-if="dislikedMovies.length">
        <h3>Disliked movies</h3>
        <div v-for="m in dislikedMovies" :key="m.id" class="movie-card">
          <img v-if="m.posterUrl" :src="m.posterUrl" :alt="m.title" class="poster" />
          <div class="movie-info">
            <h4>{{ m.title }} <span v-if="m.startYear || m.year">({{ m.startYear || m.year }})</span></h4>
            <p v-if="m.genres" class="genres">{{ m.genres.join(', ') }}</p>
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

      <router-link to="/people">&larr; Back to people</router-link>
    </template>
  </div>
</template>

<style scoped>
.person-view { max-width: 760px; margin-inline: auto; }
.person-view h1 { margin-bottom: 0; }
.username { color: var(--text-muted); margin: 0.2rem 0 1rem; }
.bio { margin: 0 0 1.5rem; color: #d1d5db; }
.person-view h3 { margin: 2rem 0 0.75rem; }

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

.actions { display: flex; gap: 0.4rem; margin-top: 0.4rem; }
.btn-like, .btn-dislike { width: 2rem; height: 2rem; padding: 0; }
.btn-like.active { background: var(--danger); }
.btn-dislike.active { background: #4b5563; }
</style>

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
.person-view { max-width: 600px; margin: 0 auto; padding: 1.5rem; }
.username { color: #666; }
.bio { margin: 0.5rem 0; }
.genre-chips { display: flex; flex-wrap: wrap; gap: 0.4rem; margin-bottom: 1rem; }
.chip { padding: 0.3rem 0.6rem; border: 1px solid #ccc; border-radius: 12px; background: #f5f5f5; }
.error { color: #b00020; }
.movie-card { display: flex; gap: 0.75rem; border: 1px solid #ddd; border-radius: 8px; padding: 0.75rem; margin-bottom: 0.5rem; }
.poster { width: 60px; height: 90px; object-fit: cover; border-radius: 4px; flex-shrink: 0; }
.movie-info { min-width: 0; }
.movie-info h4 { margin: 0 0 0.25rem; font-size: 0.95rem; }
.genres { font-size: 0.8rem; color: #666; margin: 0; }
.rating { font-size: 0.8rem; margin: 0.15rem 0; }
.empty { color: #999; font-style: italic; }
.actions { display: flex; gap: 0.5rem; margin-top: 0.4rem; }
.btn-like, .btn-dislike { border: 1px solid #ccc; border-radius: 4px; background: #f5f5f5; cursor: pointer; padding: 0.2rem 0.5rem; font-size: 1rem; }
.btn-like.active { color: #e53935; border-color: #e53935; background: #fce4ec; }
.btn-dislike.active { color: #757575; border-color: #757575; background: #eeeeee; }
</style>

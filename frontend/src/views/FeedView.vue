<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { api } from '@/api'
import { useLikeDislike } from '@/composables/useLikeDislike'

const POLL_MS = 15000

const { likedIds, dislikedIds, loadProfile, toggleLike, toggleDislike } = useLikeDislike()

const feed = ref(null)
const pending = ref(false)
const loading = ref(true)
const error = ref('')
let pollTimer = null

async function loadFeed() {
  try {
    const data = await api.get('/feed')
    if (data?.status === 'pending') {
      pending.value = true
      pollTimer = setTimeout(loadFeed, POLL_MS)
    } else {
      pending.value = false
      feed.value = data
    }
  } catch (e) {
    error.value = 'Could not load your feed right now. Please try again later.'
    console.error(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => { loadFeed(); loadProfile() })
onUnmounted(() => clearTimeout(pollTimer))
</script>

<template>
  <div class="feed-view">
    <h1>Your feed</h1>

    <p v-if="loading">Loading...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="pending" class="pending">
      Your feed is being prepared, check back in a couple of minutes.
      Make sure your <router-link to="/profile">profile</router-link> has some favourite genres.
    </p>

    <template v-else-if="feed">
      <p class="generated">Updated {{ new Date(feed.generatedAt).toLocaleString() }}</p>

      <section>
        <h2>AI picks</h2>
        <p class="summary">
          {{ feed.summary }}
          <span v-if="!feed.aiGenerated" class="fallback-note">(fallback ranking, not AI-generated)</span>
        </p>
        <div class="movie-grid">
          <div v-for="m in feed.picks" :key="m.id" class="movie-card">
            <img v-if="m.posterUrl" :src="m.posterUrl" :alt="m.title" class="poster" />
            <div class="movie-info">
              <h3>{{ m.title }} <span v-if="m.startYear">({{ m.startYear }})</span></h3>
              <p class="genres">{{ m.genres.join(', ') }}</p>
              <p v-if="m.avgRating" class="rating">★ {{ m.avgRating }}</p>
              <p v-if="m.overview" class="overview">{{ m.overview }}</p>
              <div class="actions">
                <button :class="['btn-like', { active: likedIds.has(m.id) }]" @click="toggleLike(m.id)"
                  :title="likedIds.has(m.id) ? 'Remove like' : 'Like'">&#9829;</button>
                <button :class="['btn-dislike', { active: dislikedIds.has(m.id) }]" @click="toggleDislike(m.id)"
                  :title="dislikedIds.has(m.id) ? 'Remove dislike' : 'Dislike'">&#10005;</button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section v-if="feed.fromSimilarUsers.length">
        <h2>Liked by people like you</h2>
        <div class="movie-grid">
          <div v-for="p in feed.fromSimilarUsers" :key="p.movie.id" class="movie-card">
            <img v-if="p.movie.posterUrl" :src="p.movie.posterUrl" :alt="p.movie.title" class="poster" />
            <div class="movie-info">
              <h3>{{ p.movie.title }} <span v-if="p.movie.startYear">({{ p.movie.startYear }})</span></h3>
              <p class="genres">{{ p.movie.genres.join(', ') }}</p>
              <p v-if="p.movie.avgRating" class="rating">★ {{ p.movie.avgRating }}</p>
              <p class="liked-by">Liked by {{ p.likedBy.join(', ') }}</p>
              <div class="actions">
                <button :class="['btn-like', { active: likedIds.has(p.movie.id) }]" @click="toggleLike(p.movie.id)"
                  :title="likedIds.has(p.movie.id) ? 'Remove like' : 'Like'">&#9829;</button>
                <button :class="['btn-dislike', { active: dislikedIds.has(p.movie.id) }]" @click="toggleDislike(p.movie.id)"
                  :title="dislikedIds.has(p.movie.id) ? 'Remove dislike' : 'Dislike'">&#10005;</button>
              </div>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.feed-view {
  max-width: 720px;
  margin: 0 auto;
  padding: 1.5rem;
}
section {
  margin-top: 1.5rem;
}
.generated {
  color: #666;
  font-size: 0.85rem;
}
.pending {
  color: #444;
}
.error {
  color: #b00020;
}
.summary {
  margin-bottom: 1rem;
}
.fallback-note {
  color: #999;
  font-size: 0.85rem;
}
.liked-by {
  font-size: 0.8rem;
  color: #444;
  margin-top: 0.35rem;
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

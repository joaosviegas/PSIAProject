<script setup>
defineProps({
  movie: { type: Object, required: true },
  liked: { type: Boolean, default: false },
  disliked: { type: Boolean, default: false },
  note: { type: String, default: '' },
})
defineEmits(['like', 'dislike'])
</script>

<template>
  <div class="movie-card">
    <div class="poster-wrap">
      <img v-if="movie.posterUrl" :src="movie.posterUrl" :alt="movie.title" class="poster" loading="lazy" />
      <div v-else class="poster placeholder">{{ movie.title }}</div>
      <span v-if="movie.avgRating" class="rating">&#9733; {{ movie.avgRating }}</span>
      <div class="actions">
        <button :class="['btn-like', { active: liked }]" @click="$emit('like')"
          :title="liked ? 'Remove like' : 'Like'">&#9829;</button>
        <button :class="['btn-dislike', { active: disliked }]" @click="$emit('dislike')"
          :title="disliked ? 'Remove dislike' : 'Dislike'">&#10005;</button>
      </div>
      <p v-if="movie.overview" class="overview">{{ movie.overview }}</p>
    </div>
    <div class="movie-info">
      <h3 :title="movie.title">{{ movie.title }}</h3>
      <p class="meta">
        <span v-if="movie.startYear">{{ movie.startYear }}</span>
        <span v-if="movie.startYear && movie.genres?.length"> &middot; </span>
        <span v-if="movie.genres?.length">{{ movie.genres.slice(0, 2).join(', ') }}</span>
      </p>
      <p v-if="note" class="note">{{ note }}</p>
    </div>
  </div>
</template>

<style scoped>
.movie-card { min-width: 0; }
.poster-wrap {
  position: relative;
  aspect-ratio: 2 / 3;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--bg-2);
  outline: 2px solid transparent;
  transition: outline-color 0.2s, transform 0.25s;
}
.movie-card:hover .poster-wrap {
  outline-color: var(--primary);
  transform: translateY(-4px);
}
.poster {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.4s;
}
.movie-card:hover .poster { transform: scale(1.05); }
.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  text-align: center;
  color: var(--text-dim);
  font-weight: 600;
}
.rating {
  position: absolute;
  top: 0.5rem;
  left: 0.5rem;
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--yellow);
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
}
.overview {
  position: absolute;
  inset: auto 0 0 0;
  margin: 0;
  padding: 3rem 0.75rem 0.75rem;
  font-size: 0.75rem;
  line-height: 1.4;
  color: #e5e7eb;
  background: linear-gradient(to top, rgba(11, 13, 18, 0.95) 40%, transparent);
  display: -webkit-box;
  -webkit-line-clamp: 6;
  -webkit-box-orient: vertical;
  overflow: hidden;
  opacity: 0;
  transition: opacity 0.25s;
}
.movie-card:hover .overview { opacity: 1; }
.actions {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  z-index: 1;
}
.btn-like, .btn-dislike {
  width: 2rem;
  height: 2rem;
  padding: 0;
  font-size: 0.9rem;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
}
.btn-like:hover:not(:disabled), .btn-dislike:hover:not(:disabled) { background: rgba(0, 0, 0, 0.8); }
.btn-like.active { background: var(--danger); }
.btn-dislike.active { background: #4b5563; }
.movie-info { padding: 0.6rem 0.15rem 0; }
.movie-info h3 {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.meta { margin: 0.15rem 0 0; font-size: 0.75rem; color: var(--text-muted); }
.note { margin: 0.25rem 0 0; font-size: 0.75rem; color: var(--accent); }
</style>

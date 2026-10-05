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
    <img v-if="movie.posterUrl" :src="movie.posterUrl" :alt="movie.title" class="poster" loading="lazy" />
    <div class="movie-info">
      <h3>{{ movie.title }} <span v-if="movie.startYear">({{ movie.startYear }})</span></h3>
      <p v-if="movie.genres?.length" class="genres">{{ movie.genres.join(', ') }}</p>
      <p v-if="movie.avgRating" class="rating">★ {{ movie.avgRating }}</p>
      <p v-if="movie.overview" class="overview">{{ movie.overview }}</p>
      <p v-if="note" class="note">{{ note }}</p>
      <div class="actions">
        <button :class="['btn-like', { active: liked }]" @click="$emit('like')"
          :title="liked ? 'Remove like' : 'Like'">&#9829;</button>
        <button :class="['btn-dislike', { active: disliked }]" @click="$emit('dislike')"
          :title="disliked ? 'Remove dislike' : 'Dislike'">&#10005;</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
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
.movie-info { min-width: 0; }
.movie-card h3 { margin: 0 0 0.5rem; font-size: 1rem; }
.genres { font-size: 0.85rem; color: #666; }
.overview {
  font-size: 0.8rem;
  color: #444;
  margin-top: 0.35rem;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.note { font-size: 0.8rem; color: #444; margin-top: 0.35rem; }
.actions { display: flex; gap: 0.5rem; margin-top: 0.4rem; }
.btn-like, .btn-dislike {
  border: 1px solid #ccc;
  border-radius: 4px;
  background: #f5f5f5;
  cursor: pointer;
  padding: 0.2rem 0.5rem;
  font-size: 1rem;
}
.btn-like.active { color: #e53935; border-color: #e53935; background: #fce4ec; }
.btn-dislike.active { color: #757575; border-color: #757575; background: #eeeeee; }
</style>

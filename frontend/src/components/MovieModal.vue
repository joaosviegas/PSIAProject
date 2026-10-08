<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { api } from '../api'

const props = defineProps({
  movie: { type: Object, required: true },
  liked: { type: Boolean, default: false },
  disliked: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'like', 'dislike'])

const full = ref(props.movie)

const runtime = computed(() => {
  const m = full.value.runtimeMinutes
  if (!m) return ''
  return m >= 60 ? `${Math.floor(m / 60)}h ${m % 60}m` : `${m}m`
})
const stars = computed(() => (full.value.stars || '').split('|').filter(Boolean))

function onKey(e) {
  if (e.key === 'Escape') emit('close')
}

onMounted(async () => {
  document.addEventListener('keydown', onKey)
  document.body.style.overflow = 'hidden'
  try {
    full.value = { ...props.movie, ...(await api.get(`/movies/${props.movie.id}`)) }
  } catch (e) {
    console.error('Failed to load movie details', e)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKey)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <div class="backdrop" @click.self="emit('close')">
      <div class="modal" role="dialog" aria-modal="true" :aria-label="full.title">
        <div v-if="full.posterUrl" class="bg-wrap">
          <div class="bg" :style="{ backgroundImage: `url(${full.posterUrl})` }"></div>
        </div>
        <button class="close" title="Close" @click="emit('close')">&#10005;</button>

        <div class="content">
          <img v-if="full.posterUrl" :src="full.posterUrl" :alt="full.title" class="poster" />

          <div class="info">
            <h2>{{ full.title }}</h2>

            <div class="pills">
              <span v-if="full.avgRating" class="pill rating">&#9733; {{ full.avgRating }}</span>
              <span v-if="full.numVotes" class="pill">{{ full.numVotes.toLocaleString() }} votes</span>
              <span v-if="full.startYear" class="pill">{{ full.startYear }}</span>
              <span v-if="runtime" class="pill">{{ runtime }}</span>
            </div>

            <div v-if="full.genres?.length" class="genres">
              <span v-for="g in full.genres" :key="g" class="chip">{{ g }}</span>
            </div>

            <p v-if="full.overview" class="overview">{{ full.overview }}</p>

            <dl>
              <template v-if="full.directors">
                <dt>Director</dt>
                <dd>{{ full.directors }}</dd>
              </template>
              <template v-if="stars.length">
                <dt>Starring</dt>
                <dd>{{ stars.join(', ') }}</dd>
              </template>
            </dl>

            <div class="actions">
              <button :class="['btn-like', { active: liked }]" @click="emit('like')">
                &#9829; {{ liked ? 'Liked' : 'Like' }}
              </button>
              <button :class="['btn-dislike', { active: disliked }]" @click="emit('dislike')">
                &#10005; {{ disliked ? 'Disliked' : 'Dislike' }}
              </button>
              <a v-if="full.imdbId" :href="`https://www.imdb.com/title/${full.imdbId}/`"
                target="_blank" rel="noopener" class="imdb">IMDb &#8599;</a>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: rgba(5, 7, 10, 0.75);
  backdrop-filter: blur(6px);
  animation: fade 0.2s ease-out;
}
.modal {
  position: relative;
  width: 100%;
  max-width: 1080px;
  max-height: calc(100vh - 2rem);
  overflow-x: hidden;
  overflow-y: auto;
  border-radius: var(--radius);
  background: var(--bg);
  border: 1px solid var(--border);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.6);
  animation: pop 0.25s ease-out;
}
.bg-wrap {
  position: absolute;
  inset: 0 0 auto 0;
  height: 320px;
  overflow: hidden;
  border-radius: var(--radius) var(--radius) 0 0;
}
.bg {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center 20%;
  filter: blur(30px) brightness(0.5);
  transform: scale(1.2);
  mask-image: linear-gradient(to bottom, black, transparent);
}
.close {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  z-index: 2;
  width: 2.25rem;
  height: 2.25rem;
  padding: 0;
  background: rgba(0, 0, 0, 0.5);
}
.content {
  position: relative;
  display: flex;
  gap: 2.25rem;
  padding: 2.5rem;
}
.poster {
  width: 300px;
  aspect-ratio: 2 / 3;
  object-fit: cover;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.5);
}
.info { min-width: 0; }
h2 { font-size: 2.1rem; margin: 0 2.5rem 0.75rem 0; line-height: 1.2; }
h2::before { display: none; }
.pills, .genres { display: flex; flex-wrap: wrap; gap: 0.4rem; margin-bottom: 0.75rem; }
.pill {
  padding: 0.25rem 0.8rem;
  border-radius: 999px;
  font-size: 0.8rem;
  background: var(--surface);
  backdrop-filter: blur(6px);
}
.pill.rating { color: var(--yellow); font-weight: 600; }
.genres .chip { font-size: 0.75rem; padding: 0.25rem 0.8rem; background: var(--primary-soft); color: var(--accent); }
.overview { color: #d1d5db; font-size: 1.05rem; line-height: 1.7; margin: 0.5rem 0 1.25rem; }
dl { display: grid; grid-template-columns: auto 1fr; gap: 0.5rem 1.25rem; margin: 0 0 1.75rem; font-size: 0.95rem; }
dt { color: var(--text-muted); }
dd { margin: 0; }
.actions { display: flex; flex-wrap: wrap; align-items: center; gap: 0.6rem; }
.btn-like.active { background: var(--danger); }
.btn-dislike.active { background: #4b5563; }
.imdb {
  padding: 0.55rem 1.2rem;
  border-radius: 999px;
  font-size: 0.9rem;
  font-weight: 600;
  color: #000;
  background: #f5c518;
}
.imdb:hover { color: #000; opacity: 0.85; }

@media (max-width: 640px) {
  .content { flex-direction: column; align-items: center; padding: 1.5rem 1.25rem; }
  .poster { width: 160px; }
  h2 { font-size: 1.4rem; margin-right: 0; text-align: center; }
}

@keyframes fade { from { opacity: 0; } }
@keyframes pop { from { opacity: 0; transform: translateY(12px) scale(0.98); } }
</style>

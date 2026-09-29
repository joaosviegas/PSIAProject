import { ref } from 'vue'
import { api } from '../api'
import { auth } from '../auth'

export function useLikeDislike() {
  const likedIds = ref(new Set())
  const dislikedIds = ref(new Set())
  const profileData = ref(null)

  async function loadProfile() {
    try {
      profileData.value = await api.get('/profiles/me')
      likedIds.value = new Set(profileData.value.favoriteMovieIds || [])
      dislikedIds.value = new Set(profileData.value.dislikedMovieIds || [])
    } catch (e) { /* 404 = no profile yet */ }
  }

  async function saveProfile() {
    try {
      await api.put('/profiles/me', {
        displayName: profileData.value?.displayName || auth.username,
        bio: profileData.value?.bio || '',
        favoriteGenres: profileData.value?.favoriteGenres || [],
        favoriteMovieIds: [...likedIds.value],
        dislikedMovieIds: [...dislikedIds.value],
      })
    } catch (e) { console.error('Failed to save preference', e) }
  }

  function toggleLike(id) {
    if (likedIds.value.has(id)) { likedIds.value.delete(id) }
    else { likedIds.value.add(id); dislikedIds.value.delete(id) }
    likedIds.value = new Set(likedIds.value)
    dislikedIds.value = new Set(dislikedIds.value)
    saveProfile()
  }

  function toggleDislike(id) {
    if (dislikedIds.value.has(id)) { dislikedIds.value.delete(id) }
    else { dislikedIds.value.add(id); likedIds.value.delete(id) }
    likedIds.value = new Set(likedIds.value)
    dislikedIds.value = new Set(dislikedIds.value)
    saveProfile()
  }

  return { likedIds, dislikedIds, loadProfile, toggleLike, toggleDislike }
}

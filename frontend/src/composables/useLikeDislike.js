import { ref } from 'vue'
import { api } from '../api'
import { auth } from '../auth'

export function useLikeDislike() {
  const likedIds = ref(new Set())
  const dislikedIds = ref(new Set())
  const profileData = ref(null)

  let loading = null
  let saveChain = Promise.resolve()

  function loadProfile() {
    loading = api.get('/profiles/me')
      .then((profile) => {
        profileData.value = profile
        likedIds.value = new Set(profile.favoriteMovieIds || [])
        dislikedIds.value = new Set(profile.dislikedMovieIds || [])
      })
      .catch((e) => { console.error('Failed to load profile', e) })
    return loading
  }

  // Saves are queued so quick clicks can't land out of order, and nothing is ever written
  // before we know what is already saved: a PUT built from an unloaded profile would
  // blank the user's favourite genres and bio (and with them, their feed).
  function saveProfile() {
    saveChain = saveChain.then(async () => {
      if (!profileData.value) await (loading ?? loadProfile())
      if (!profileData.value) return
      try {
        const saved = await api.put('/profiles/me', {
          displayName: profileData.value.displayName || auth.username,
          bio: profileData.value.bio || '',
          favoriteGenres: profileData.value.favoriteGenres || [],
          favoriteMovieIds: [...likedIds.value],
          dislikedMovieIds: [...dislikedIds.value],
        })
        if (saved) profileData.value = saved
      } catch (e) { console.error('Failed to save preference', e) }
    })
    return saveChain
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

  return { likedIds, dislikedIds, profileData, loadProfile, toggleLike, toggleDislike }
}

import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '@/auth'

import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import FeedView from '@/views/FeedView.vue'
import AskView from '@/views/AskView.vue'
import MoviesView from '@/views/MoviesView.vue'
import ProfileView from '@/views/ProfileView.vue'
import PeopleView from '@/views/PeopleView.vue'
import PersonView from '@/views/PersonView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/register', component: RegisterView, meta: { public: true } },
    { path: '/', component: FeedView },
    { path: '/ask', component: AskView },
    { path: '/movies', component: MoviesView },
    { path: '/profile', component: ProfileView },
    { path: '/people', component: PeopleView },
    { path: '/people/:userId', component: PersonView },
  ],
})

router.beforeEach((to) => {
  if (!to.meta.public && !auth.isLoggedIn) {
    return '/login'
  }
})

export default router

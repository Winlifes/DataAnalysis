import { createRouter, createWebHistory } from 'vue-router'
import Login from '@/views/Login.vue'
import Dashboard from '@/views/Dashboard.vue'
import Analysis from '@/views/Analysis.vue'
import Users from '@/views/Users.vue'
import Data from '@/views/Data.vue'
import UserCenter from "@/views/UserCenter.vue";

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: Login },
  { path: '/dashboard', component: Dashboard },
  { path: '/analysis', component: Analysis },
  { path: '/users', component: Users },
  { path: '/data', component: Data },
  { path: '/userCenter', component: UserCenter},
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router

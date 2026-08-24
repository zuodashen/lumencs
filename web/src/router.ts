import { createRouter, createWebHistory } from 'vue-router'
import ChatView from './views/ChatView.vue'
import LoginView from './views/LoginView.vue'
import ConsoleLayout from './views/console/ConsoleLayout.vue'
import DashboardView from './views/console/DashboardView.vue'
import KnowledgeView from './views/console/KnowledgeView.vue'
import TicketsView from './views/console/TicketsView.vue'
import TracesView from './views/console/TracesView.vue'
import MemoryView from './views/console/MemoryView.vue'
import ToolsView from './views/console/ToolsView.vue'
import ReviewsView from './views/console/ReviewsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'chat', component: ChatView },
    { path: '/embed', name: 'embed', component: ChatView },
    { path: '/console/login', name: 'login', component: LoginView },
    {
      path: '/console',
      component: ConsoleLayout,
      children: [
        { path: '', redirect: '/console/overview' },
        { path: 'overview', component: DashboardView },
        { path: 'knowledge', component: KnowledgeView },
        { path: 'tickets', component: TicketsView },
        { path: 'traces', component: TracesView },
        { path: 'memory', component: MemoryView },
        { path: 'tools', component: ToolsView },
        { path: 'reviews', component: ReviewsView },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (to.path.startsWith('/console') && to.path !== '/console/login') {
    if (!localStorage.getItem('lumencs_token')) {
      return '/console/login'
    }
  }
  return true
})

export default router

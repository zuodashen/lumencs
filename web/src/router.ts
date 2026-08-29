import { createRouter, createWebHistory } from 'vue-router'
import ChatView from './views/ChatView.vue'
import LoginView from './views/LoginView.vue'
import HubShell from './components/HubShell.vue'
import HubHomeView from './views/HubHomeView.vue'
import AppsView from './views/AppsView.vue'
import KnowledgeView from './views/console/KnowledgeView.vue'
import TicketsView from './views/console/TicketsView.vue'
import TracesView from './views/console/TracesView.vue'
import MemoryView from './views/console/MemoryView.vue'
import ToolsView from './views/console/ToolsView.vue'
import ReviewsView from './views/console/ReviewsView.vue'
import InboxView from './views/console/InboxView.vue'
import GapsView from './views/console/GapsView.vue'
import ChannelsView from './views/console/ChannelsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/embed', name: 'embed', component: ChatView },
    { path: '/console/login', name: 'login', component: LoginView },
    {
      path: '/',
      component: HubShell,
      children: [
        { path: '', name: 'home', component: HubHomeView },
        { path: 'chat', name: 'chat', component: ChatView },
        { path: 'apps', name: 'apps', component: AppsView },
        { path: 'console', redirect: '/' },
        { path: 'console/overview', redirect: '/' },
        { path: 'console/inbox', component: InboxView },
        { path: 'console/gaps', component: GapsView },
        { path: 'console/channels', component: ChannelsView },
        { path: 'console/knowledge', component: KnowledgeView },
        { path: 'console/tickets', component: TicketsView },
        { path: 'console/traces', component: TracesView },
        { path: 'console/memory', component: MemoryView },
        { path: 'console/tools', component: ToolsView },
        { path: 'console/reviews', component: ReviewsView },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('lumencs_token')
  if (to.path === '/console/login') {
    if (token) {
      const raw = to.query.next
      const next = typeof raw === 'string' && raw.startsWith('/') && !raw.startsWith('//') && !raw.includes('\\')
        ? raw
        : '/'
      return next
    }
    return true
  }
  if (!token) {
    return { path: '/console/login', query: { next: to.fullPath } }
  }
  return true
})

export default router

import { createRouter, createWebHistory } from 'vue-router';
import Dashboard from '../views/Dashboard.vue';
import Portfolio from '../views/Portfolio.vue';
import Market from '../views/Market.vue';
import Analytics from '../views/Analytics.vue';

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: Dashboard
  },
  {
    path: '/portfolio',
    name: 'Portfolio',
    component: Portfolio
  },
  {
    path: '/portfolio/:id',
    name: 'PortfolioDetail',
    component: Portfolio
  },
  {
    path: '/market',
    name: 'Market',
    component: Market
  },
  {
    path: '/analytics',
    name: 'Analytics',
    component: Analytics
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

export default router;

<template>
  <div class="app">
    <header class="header">
      <div class="header-row">
        <h1>{{ t('common.title') }}</h1>
        <nav class="nav">
          <router-link to="/dashboard" class="nav-link" active-class="active">{{ t('nav.dashboard') }}</router-link>
          <router-link to="/portfolio" class="nav-link" active-class="active">{{ t('nav.portfolio') }}</router-link>
          <router-link to="/market" class="nav-link" active-class="active">{{ t('nav.market') }}</router-link>
          <router-link to="/analytics" class="nav-link" active-class="active">{{ t('nav.analytics') }}</router-link>
        </nav>
        <div class="header-actions">
          <LangSwitcher />
        </div>
      </div>
      <p class="sub">
        {{ t('common.subtitle') }}
      </p>
    </header>

    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import LangSwitcher from './components/LangSwitcher.vue';
import { t } from './locales';
</script>

<style>
* {
  box-sizing: border-box;
}

body {
  margin: 0;
  font-family: system-ui, -apple-system, sans-serif;
  background: #f5f5f5;
}

.app {
  min-height: 100vh;
}

.header {
  background: white;
  border-bottom: 1px solid #e5e5e5;
  padding: 1rem 1.5rem;
  position: sticky;
  top: 0;
  z-index: 10;
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1400px;
  margin: 0 auto;
  gap: 1rem;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.header h1 {
  margin: 0;
  font-size: 1.5rem;
  color: #1a1a1a;
}

.nav {
  display: flex;
  gap: 0.5rem;
  flex: 1;
  justify-content: center;
}

.nav-link {
  padding: 0.5rem 1rem;
  color: #666;
  text-decoration: none;
  border-radius: 8px;
  transition: all 0.2s;
}

.nav-link:hover {
  background: #f3f4f6;
  color: #1a1a1a;
}

.nav-link.active {
  background: #4f46e5;
  color: white;
}

.sub {
  max-width: 1400px;
  margin: 0.5rem auto 0;
  font-size: 0.875rem;
  color: #666;
}

.main-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 1.5rem;
}

@media (max-width: 768px) {
  .header-row {
    flex-direction: column;
    gap: 1rem;
  }
  
  .nav {
    flex-wrap: wrap;
    justify-content: center;
  }
}
</style>

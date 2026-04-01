<template>
  <button class="lang-btn" @click="toggleLang">
    {{ currentLang.value === 'zh' ? 'EN' : '中文' }}
  </button>
</template>

<script setup>
import { currentLang, setLang } from '../locales';

function toggleLang() {
  const newLang = currentLang.value === 'zh' ? 'en' : 'zh';
  setLang(newLang);
}
</script>

<style scoped>
.lang-btn {
  padding: 0.4rem 0.8rem;
  border: 1px solid #e5e5e5;
  background: white;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.875rem;
}
.lang-btn:hover {
  background: #f3f4f6;
}
</style>

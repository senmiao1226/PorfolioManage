import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";

// 初始化语言设置
import { initLang } from './locales';
initLang();

const app = createApp(App);
app.use(router);
app.mount("#app");

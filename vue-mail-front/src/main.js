import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/dist/locale/zh-cn.mjs';
import 'element-plus/dist/index.css';
import 'nprogress/nprogress.css';
import '@vueup/vue-quill/dist/vue-quill.snow.css';

import App from './App.vue';
import router from './router';
import pinia from './stores';
import './styles/index.scss';

const app = createApp(App);

app.use(pinia);
app.use(router);
app.use(ElementPlus, { locale: zhCn });

app.mount('#app');

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Workbench from '../src/views/p0/P0Workbench.vue'
const router = createRouter({ history: createWebHashHistory(), routes: [{ path: '/:pathMatch(.*)*', component: Workbench, meta: { section: 'p0Finance' } }] })
const app = createApp(Workbench).use(createPinia()).use(router).use(ElementPlus)
router.isReady().then(() => app.mount('#app'))

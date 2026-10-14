import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// https://vitejs.dev/config/
export default defineConfig({
  base: process.env.VITE_SANDBOX_MODE === 'true' ? '/sandbox/' : './',
  build: {
    // 开发阶段启用源码映射：https://uniapp.dcloud.net.cn/tutorial/migration-to-vue3.html#需主动开启-sourcemap
    sourcemap: process.env.NODE_ENV === 'development',
  },
  plugins: [uni()],
  server: {
    // 本地开发代理 OSS 图片：转发时清空 Referer，绕过阿里云防盗链(403)
    proxy: {
      '/oss': {
        target: 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/oss/, ''),
        headers: { Referer: '' },
      },
    },
  },
})

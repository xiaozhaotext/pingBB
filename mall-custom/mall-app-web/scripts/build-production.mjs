import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

// 固定正式站原有支付开关，避免全量构建时意外关闭微信支付或启用模拟支付。
const root = fileURLToPath(new URL('../', import.meta.url))
const result = spawnSync(process.execPath, ['node_modules/@dcloudio/vite-plugin-uni/bin/uni.js', 'build'], {
  cwd: root,
  stdio: 'inherit',
  env: { ...process.env, VITE_USE_WECHAT_PAY: 'true', VITE_USE_MOCK_PAYMENT: 'false' },
})
if (result.error) throw result.error
if (result.status !== 0) process.exit(result.status ?? 1)
await import('./copy-static.mjs')

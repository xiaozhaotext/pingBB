import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { readFileSync, writeFileSync, readdirSync, unlinkSync, existsSync } from 'node:fs'
import path from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const result = spawnSync(process.execPath, ['node_modules/@dcloudio/vite-plugin-uni/bin/uni.js', 'build', '-p', 'mp-weixin'], {
  cwd: root,
  stdio: 'inherit',
  env: {
    ...process.env,
    VITE_API_BASE_URL: 'https://zhiyoumei.com.cn/portal',
    VITE_SANDBOX_MODE: 'false',
    VITE_USE_WECHAT_PAY: 'true',
    VITE_USE_ALIPAY: 'false',
    VITE_USE_MOCK_PAYMENT: 'false',
  },
})
if (result.error) throw result.error
if (result.status !== 0) process.exit(result.status ?? 1)

const output = path.join(root, 'dist/build/mp-weixin')
function files(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const name = path.join(directory, entry.name)
    return entry.isDirectory() ? files(name) : [name]
  })
}
// 原生 tabBar 必须使用本地图片；服务图标也随包携带，避免网络延迟导致空白。
const app = JSON.parse(readFileSync(path.join(output, 'app.json'), 'utf8'))
const localImages = new Set((app.tabBar?.list || []).flatMap(item => [item.iconPath, item.selectedIconPath]).filter(Boolean))
const remoteImages = files(path.join(output, 'static'))
  .map(name => path.relative(output, name).replaceAll('\\', '/'))
  .filter(name => /\.(png|jpe?g|webp|gif)$/i.test(name) && !name.startsWith('static/services/') && !localImages.has(name))
for (const name of files(output)) {
  if (!['.js', '.wxss', '.wxml'].includes(path.extname(name))) continue
  let source = readFileSync(name, 'utf8')
  for (const image of remoteImages) {
    // 避免给已经是完整地址的资源重复添加域名。
    const escaped = image.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    source = source.replace(new RegExp(`(["'\x60])/${escaped}`, 'g'), `$1https://zhiyoumei.com.cn/${image}`)
  }
  if (name.endsWith('.wxss')) source = source.replace(/url\(([^)]+)\)/g, (match, raw) => {
    const value=raw.trim().replace(/^["']|["']$/g,'')
    const index=value.indexOf('static/')
    return index>=0 && !/^(https?:|data:)/.test(value) ? `url("https://zhiyoumei.com.cn/${value.slice(index)}")` : match
  })
  writeFileSync(name, source)
}
for (const image of remoteImages) if (existsSync(path.join(output, image))) unlinkSync(path.join(output, image))

const configPath = path.join(output, 'project.config.json')
const config = JSON.parse(readFileSync(configPath, 'utf8'))
config.appid = 'wx734dae9c5f5285b6'
config.projectname = 'zhiyoumei-official'
config.miniprogramRoot = './'
config.setting = { ...config.setting, urlCheck: true, es6: true, minified: true }
writeFileSync(configPath, JSON.stringify(config, null, 2))
const check = spawnSync(process.execPath, ['scripts/test-built-home.cjs'], { cwd: root, stdio: 'inherit' })
if (check.error) throw check.error
if (check.status !== 0) process.exit(check.status ?? 1)
console.log('WeChat release prepared with official API and hosted product images.')

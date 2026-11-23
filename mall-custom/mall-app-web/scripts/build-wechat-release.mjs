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
const remoteImages = [
  'static/hot_product_banner.png',
  'static/new_product_banner.png',
  'static/recommend_brand_banner.png',
  'static/temp/banner4.jpg',
  'static/travel-photo/alpine-lake.jpg',
  'static/travel-photo/coastal-retreat.jpg',
  'static/travel-photo/ocean-terrace.jpg',
]
function files(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const name = path.join(directory, entry.name)
    return entry.isDirectory() ? files(name) : [name]
  })
}
for (const name of files(output)) {
  if (!['.js', '.wxss', '.wxml'].includes(path.extname(name))) continue
  let source = readFileSync(name, 'utf8')
  for (const image of remoteImages) {
    source = source.replaceAll(`/${image}`, `https://zhiyoumei.com.cn/${image}`)
  }
  source = source.replace(/(["'`])\/static\/festive-home\//g, '$1https://zhiyoumei.com.cn/static/festive-home/')
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
console.log('WeChat release prepared with official API and hosted product images.')

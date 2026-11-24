const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const QRCode = require('qrcode')
const output = path.join(__dirname, '../dist/build/mp-weixin')
const calls = []
let slides = ['/static/festive-home/hero.jpg', '/static/festive-home/hero-test.jpg']
let version = 'v1', failed = false
const rects = []; let color
const uni = {
  request(options) {
    const url = new URL(options.url)
    assert.equal(url.origin, 'https://zhiyoumei.com.cn')
    assert.match(url.pathname, /^\/static\/festive-home\/(hero-carousel|pick-captions|rebuy-captions|consume-captions)\.json$/)
    calls.push(url)
    if (failed) return options.fail({})
    options.success({ statusCode: 200, header: { ETag: version }, data: url.pathname.endsWith('hero-carousel.json') ? slides : [] })
  },
  createCanvasContext() { return {setFillStyle(value) {color=value},fillRect(x,y,w,h) {rects.push({x,y,w,h,color})},draw(_reserve,done) {done()} } },
}
function load(file) {
 const module={exports:{}}
 const vendor=new Proxy({index:uni,wx$1:uni},{get:(target,key)=>key in target?target[key]:QRCode})
 vm.runInNewContext(fs.readFileSync(path.join(output,file),'utf8'),{module,exports:module.exports,require:()=>vendor,uni,Date,Uint8Array,encodeURIComponent,parseInt})
 return module.exports
}
;(async()=>{
 const api=load('utils/miniHomePresentation.js')
 const first=await api.load(true)
 assert.equal(first.images.length,2)
 first.images.forEach(url=>assert.equal(new URL(url).origin,'https://zhiyoumei.com.cn'))
 version='v2';slides=['/static/festive-home/hero-changed.jpg'];const second=await api.load(true)
 assert.equal(calls.length,8);assert.equal(second.images.length,1);assert.match(second.images[0],/hero-changed.jpg\?v=v2$/)
 failed=true;assert.deepEqual((await api.load(true)).images,second.images)
 failed=false;slides=[];assert.equal((await api.load(true)).images.length,0)
 const qr=load('utils/invitationQr.js'),link='https://zhiyoumei.com.cn/#/pages/public/register?inviteCode=LOCALQRT'
 const previousEncoder=global.TextEncoder
 try {global.TextEncoder=undefined;await qr.drawInvitationQr(link,'test')} finally {global.TextEncoder=previousEncoder}
 assert.equal(rects[0].color,'#ffffff');assert(rects.filter(r=>r.color==='#000000').length>100)
 assert(rects.every(r=>r.x>=0&&r.y>=0&&r.x+r.w<=240&&r.y+r.h<=240))
 console.log('Built-package checks passed: valid request origins, refreshed carousel, empty/failure handling, native QR without TextEncoder.')
})().catch(error=>{console.error(error);process.exit(1)})

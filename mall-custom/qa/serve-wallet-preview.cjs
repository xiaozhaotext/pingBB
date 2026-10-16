// Local-only preview for SandboxWalletBrowserHarness. No production credentials or data.
const http = require('node:http')
const fs = require('node:fs')
const path = require('node:path')
const root = path.resolve(__dirname, '..')
const types = { '.html':'text/html; charset=utf-8', '.js':'text/javascript', '.css':'text/css', '.json':'application/json', '.svg':'image/svg+xml', '.png':'image/png', '.woff2':'font/woff2', '.ttf':'font/ttf' }
http.createServer((req,res) => {
  const url = new URL(req.url, 'http://localhost')
  const prefix = ['/sandbox/portal','/sandbox/admin-api','/admin-api'].find(p => url.pathname.startsWith(p+'/'))
  if (prefix) {
    const upstream = http.request({ hostname:'127.0.0.1', port:18086, path:req.url.slice(prefix.length), method:req.method, headers:req.headers }, response => {
      res.writeHead(response.statusCode,response.headers); response.pipe(res)
    })
    upstream.on('error',err => { res.writeHead(502);res.end(err.message) })
    req.pipe(upstream);return
  }
  const admin = url.pathname.startsWith('/admin/')
  const base = path.join(root,admin?'mall-admin-web/dist':'mall-app-web/dist/build/h5')
  const relative = decodeURIComponent(url.pathname.replace(admin?'/admin/':'/sandbox/',''))
  let file = path.resolve(base,relative || 'index.html')
  if (!file.startsWith(base+path.sep)) { res.writeHead(403);res.end();return }
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) file=path.join(base,'index.html')
  res.setHeader('Content-Type',types[path.extname(file)] || 'application/octet-stream')
  fs.createReadStream(file).on('error',()=>{res.statusCode=404;res.end()}).pipe(res)
}).listen(18090,'127.0.0.1',()=>console.log('Wallet UI preview: http://127.0.0.1:18090/sandbox/ and /admin/'))

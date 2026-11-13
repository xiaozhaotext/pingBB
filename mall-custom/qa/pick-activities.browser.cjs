// 使用构建产物和模拟接口验证页面交互，绝不请求线上服务或创建真实订单。
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const root = path.resolve(__dirname, '..');
const output = path.join(__dirname, 'pick-activity-results');
fs.mkdirSync(output, { recursive: true });
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.json': 'application/json', '.woff2': 'font/woff2' };
function serve(directory) {
  return new Promise(resolve => {
    const server = http.createServer((req, res) => {
      const name = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
      const target = path.resolve(directory, '.' + (name === '/' ? '/index.html' : name));
      if (!target.startsWith(directory + path.sep) || !fs.existsSync(target) || !fs.statSync(target).isFile()) { res.writeHead(404); res.end(); return; }
      res.setHeader('Content-Type', mime[path.extname(target)] || 'application/octet-stream');
      fs.createReadStream(target).pipe(res);
    }).listen(0, '127.0.0.1', () => resolve({ server, url: `http://127.0.0.1:${server.address().port}` }));
  });
}
const products = [1,2,3,4].map(id => ({ id, name: `旅游正品${id}`, pic: `/product-images/${id}.png`, price: 3999, publishStatus: 1 }));
const activity = { id: 9, title: '旅游活动详情1', imageUrl: '/product-images/activity.png', rules: '活动规则第一行\n活动规则第二行', enabled: true, sortOrder: 10, products };
const image = '<svg xmlns="http://www.w3.org/2000/svg" width="900" height="360"><rect width="900" height="360" fill="#d5e7dd"/><path d="M0 360L260 80 470 300 640 100 900 360" fill="#82ab94"/></svg>';
async function mock(context, admin = false) {
  const requests = [];
  const state = { activities: [activity], detail: activity, fail: false, saved: null };
  await context.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url());
    if (url.hostname !== '127.0.0.1') return route.abort();
    if (url.pathname.startsWith('/product-images/')) return route.fulfill({ contentType: 'image/svg+xml', body: image });
    if (!url.pathname.startsWith('/portal/') && !url.pathname.startsWith('/admin-api/')) return route.continue();
    const p = url.pathname.replace(/^\/(portal|admin-api)/, '');
    requests.push({ path: p, method: request.method(), body: request.postData() });
    let data = {};
    if (p === '/product/pickActivity') {
      if (state.fail) return route.fulfill({ status: 500, json: { code: 500, message: '测试网络故障' } });
      data = state.activities;
    } else if (p === '/product/pickActivity/9') data = state.detail;
    else if (p.startsWith('/product/detail/')) {
      const id = Number(p.split('/').pop());
      data = { product: { ...products.find(x => x.id === id), subTitle: `正品${id}介绍`, stock: 10, sale: 1, detailMobileHtml: `<p>正品${id}完整内容介绍</p>`, albumPics: '', serviceIds: '', promotionType: 0 }, skuStockList: [{ id: id * 10, price: 3999, stock: 10, spData: '[]' }], productAttributeList: [], productAttributeValueList: [], membershipRoute: true, brand: {}, shopName: '平台自营' };
    } else if (p === '/cart/list') data = [{ id: 101, productId: 2, productSkuId: 20, zoneCode: 'PICK' }];
    else if (p === '/member/productCollection/detail') data = null;
    else if (p.includes('productCollection/add') || p === '/cart/add') data = 1;
    else if (p === '/product/zone/PICK') data = [];
    else if (p === '/product/search' || p === '/product/comment/list') data = { list: [], total: 0 };
    else if (p === '/admin/info') data = { roles: ['admin'], username: 'admin', departmentId: null, menus: ['pick','pickCategory','pickProduct','giftOptionsMgr','giftOptions'].map((name,id) => ({ id, name, title: name === 'pick' ? '自选商品管理' : name === 'pickCategory' ? '分类管理' : name === 'pickProduct' ? '商品列表' : '赠品商品', hidden: 0 })) };
    else if (p === '/productCategory/pickActivity/products') data = products;
    else if (p === '/productCategory/pickActivity/9') data = state.detail;
    else if (p === '/productCategory/pickActivity') {
      if (request.method() === 'POST') { state.saved = JSON.parse(request.postData()); data = 9; }
      else data = state.activities;
    } else if (p === '/product/image/upload') data = { url: '/product-images/new.png' };
    else if (p === '/order/generateConfirmOrder') data = { cartPromotionItemList: [], memberReceiveAddressList: [], couponHistoryDetailList: [], calcAmount: {}, memberIntegration: 0 };
    else if (p === '/order/settlementPreview') data = { zones: [], totalPayAmount: 0 };
    else if (p.includes('/list') || p.includes('coupon') || p.includes('fullReduction')) data = [];
    await route.fulfill({ json: { code: 200, message: '成功', data } });
  });
  return { requests, state };
}
async function main() {
  const app = await serve(path.join(root, 'mall-app-web/dist/build/h5'));
  const admin = await serve(path.join(root, 'mall-admin-web/dist'));
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const results = [];
  try {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const { requests, state } = await mock(context);
    await context.addInitScript(() => {
      // uni-app 的网页存储包含类型包装，与实际持久化格式一致。
      localStorage.setItem('member', JSON.stringify({ type: 'string', data: JSON.stringify({ memberInfo: { id: 1, nickname: '测试会员' } }) }));
      localStorage.setItem('token', JSON.stringify({ type: 'string', data: 'Bearer local-test' }));
    });
    const page = await context.newPage();
    const errors = []; page.on('pageerror', e => errors.push(e.message));
    await page.goto(app.url + '/#/pages/zone/pick');
    await page.getByText('旅游活动详情1', { exact: true }).waitFor();
    await page.getByText('旅游活动详情1', { exact: true }).click();
    await page.locator('.introduce-section .title').filter({ hasText: '旅游正品1' }).waitFor();
    assert.equal(await page.locator('.activity-option.selected').innerText(), '旅游正品1');
    assert.match(await page.locator('.activity-rule-text').innerText(), /活动规则第二行/);
    await page.screenshot({ path: path.join(output, 'mobile-activity.png'), fullPage: true });
    await page.locator('.activity-option').filter({ hasText: '旅游正品2' }).click();
    await page.locator('.introduce-section .title').filter({ hasText: '旅游正品2' }).waitFor();
    assert.equal(await page.locator('.activity-option.selected').innerText(), '旅游正品2');
    assert.match(await page.locator('.detail-desc').innerText(), /正品2完整内容介绍/);
    assert.equal(await page.locator('.carousel uni-image').count(), 1);
    await Promise.all([page.waitForResponse(r => r.url().includes('/productCollection/add')), page.locator('.page-bottom .p-b-btn').filter({ hasText: '收藏' }).click()]);
    assert.equal(JSON.parse(requests.find(x => x.path.endsWith('productCollection/add')).body).productId, 2);
    await Promise.all([page.waitForResponse(r => r.url().endsWith('/cart/add')), page.locator('.add-cart-btn').click()]);
    const cart = requests.find(x => x.path === '/cart/add');
    assert.equal(JSON.parse(cart.body).productSkuId, 20);
    assert.equal(JSON.parse(cart.body).zoneCode, 'PICK');
    await page.locator('.buy-now-btn').click();
    await page.waitForURL(/createOrder/);
    assert(!requests.some(x => /giftOptions|gift-options/.test(x.path)));
    results.push('活动入口、默认首件、商品切换、图文更新、收藏及购买目标正确，未请求赠品接口');
    state.detail = { ...activity, products: [] };
    await page.goto(app.url + '/#/pages/product/product?zone=PICK&activityId=9');
    await page.getByText('本活动暂无可售正品').waitFor();
    assert.equal(await page.locator('.page-bottom').count(), 0);
    results.push('无可售正品时不显示购买入口');
    state.activities = [];
    await page.goto(app.url + '/#/pages/zone/pick');
    await page.getByText('暂无已发布的旅游活动').waitFor();
    state.fail = true;
    await page.reload();
    await page.getByText('活动加载失败，点击重试').waitFor();
    state.fail = false; state.activities = [activity];
    await page.getByText('活动加载失败，点击重试').click();
    await page.getByText('旅游活动详情1', { exact: true }).waitFor();
    results.push('活动空状态、失败提示及重试恢复正常');
    const adminContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const adminMock = await mock(adminContext, true);
    await adminContext.addInitScript(() => localStorage.setItem('user', JSON.stringify({ userInfo: { username: 'admin', token: 'Bearer local-test', roles: ['admin'], menus: [] } })));
    const backoffice = await adminContext.newPage();
    backoffice.on('pageerror', e => errors.push(e.message));
    await backoffice.goto(admin.url + '/#/pick/category');
    await backoffice.getByRole('heading', { name: '分类管理 · 旅游活动' }).waitFor();
    assert.equal(await backoffice.getByText('赠品商品', { exact: true }).count(), 0);
    await backoffice.getByRole('button', { name: '编辑', exact: true }).click();
    await backoffice.locator('.el-dialog').waitFor();
    await backoffice.locator('.product-row').nth(1).getByRole('button', { name: '上移', exact: true }).click();
    await backoffice.screenshot({ path: path.join(output, 'admin-activity.png'), fullPage: true });
    try {
      await Promise.all([backoffice.waitForResponse(r => r.url().includes('/productCategory/pickActivity') && r.request().method() === 'POST'), backoffice.getByRole('button', { name: '保存活动', exact: true }).click()]);
    } catch (error) {
      console.log(JSON.stringify({ requests: adminMock.requests, errors, text: await backoffice.locator('body').innerText() }));
      throw error;
    }
    assert.deepEqual(adminMock.state.saved.productIds, [2,1,3,4]);
    assert.equal(adminMock.state.saved.title, '旅游活动详情1');
    results.push('后台活动编辑与正品排序保存正常，旧赠品菜单不可见');
    await backoffice.locator('.el-dialog').waitFor({ state: 'hidden' });
    await backoffice.getByRole('button', { name: '新增活动', exact: true }).click();
    await backoffice.locator('.el-dialog').waitFor();
    const postCount = adminMock.requests.filter(x => x.method === 'POST').length;
    await backoffice.getByRole('button', { name: '保存活动', exact: true }).click();
    await backoffice.getByText('请填写活动名称、图片、规则，并至少选择一件正品', { exact: true }).waitFor();
    assert.equal(adminMock.requests.filter(x => x.method === 'POST').length, postCount);
    await backoffice.locator('.el-dialog input[maxlength="120"]').fill('旅游活动详情2');
    await backoffice.locator('.el-dialog textarea').fill('新增活动规则');
    await Promise.all([
      backoffice.waitForResponse(r => r.url().includes('/product/image/upload')),
      backoffice.locator('input[type="file"]').setInputFiles({ name: 'activity.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aB9sAAAAASUVORK5CYII=', 'base64') }),
    ]);
    await backoffice.locator('.product-search .el-select').click();
    await backoffice.getByRole('option', { name: '旅游正品2（2）', exact: true }).click();
    await backoffice.getByRole('button', { name: '添加正品', exact: true }).click();
    await Promise.all([backoffice.waitForResponse(r => r.url().includes('/productCategory/pickActivity') && r.request().method() === 'POST'), backoffice.getByRole('button', { name: '保存活动', exact: true }).click()]);
    assert.deepEqual(adminMock.state.saved.productIds, [2]);
    assert.equal(adminMock.state.saved.imageUrl, '/product-images/new.png');
    assert.equal(adminMock.state.saved.rules, '新增活动规则');
    results.push('后台必填校验、图片上传、添加正品及新建活动保存正常');
    state.detail = activity;
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.goto(app.url + '/#/pages/product/product?zone=PICK&activityId=9');
    await page.locator('.introduce-section .title').filter({ hasText: '旅游正品1' }).waitFor();
    const activityBox = await page.locator('.travel-activity').boundingBox();
    const productBox = await page.locator('.carousel').boundingBox();
    assert(productBox.y >= activityBox.y + activityBox.height - 2);
    await page.screenshot({ path: path.join(output, 'desktop-activity.png'), fullPage: true });
    await page.goto(app.url + '/#/pages/index/index');
    await page.getByText('旅游活动详情1', { exact: true }).waitFor();
    await page.getByText('旅游活动详情1', { exact: true }).click();
    await page.waitForURL(/activityId=9/);
    results.push('桌面活动区位于商品详情上方，首页活动推荐进入对应活动');
    assert.deepEqual(errors, []);
    fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify({ passed: results, pageErrors: errors }, null, 2));
    console.log(JSON.stringify(results, null, 2));
  } finally { await browser.close(); app.server.close(); admin.server.close(); }
}
main().catch(e => { console.error(e); process.exitCode = 1; });

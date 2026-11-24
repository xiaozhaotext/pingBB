"use strict";
// 首页运营文案的唯一远端来源：后台「栏目图片 / 叠加文字」写入的静态 JSON。
// H5 端 storefront/static/column-captions.js 读取同一目录下的同一批文件，
// 因此后台改一次，H5 与小程序同时生效。
const host = "https://zhiyoumei.com.cn";
const STATIC = "/static/festive-home/";
const hero = host + STATIC + "hero.jpg";
const CACHE_KEY = "wbHomeCopyCache";

// 内置兜底：后台配置缺失 / 请求失败时保持现有页面观感。
// 必须与 H5 column-captions.js 里的 DEFAULT_COPY 保持一致。
const DEFAULT_COPY = {
  brand: "智游美",
  searchHint: "⌕ 搜索商品名称",
  cartEntry: "购物车 →",
  heroTitle: "山水有约，好物相伴",
  heroSub: "品质好物 · 悦享生活",
  announcementLabel: "公告",
  announcementMore: "查看 ›",
  quickCategoryTitle: "商品分类",
  quickCategorySub: "按品类找好物 →",
  quickCartTitle: "购物车",
  quickCartSub: "查看已选商品 →",
  pickHeading: "自选区旅游活动",
  pickHeadingSub: "精选好物",
  rebuyHeading: "复购区推荐商品",
  rebuyHeadingSub: "精选好物",
  consumeHeading: "消费区推荐商品",
  consumeHeadingSub: "智豆优先 · 挑选好物",
  more: "更多 →",
  pickItemSub: "查看活动规则与旅游正品",
  pickBadge: "自选区活动",
  rebuyBadge: "复购区",
  consumeBadge: "消费区",
  addCart: "加入购物车",
};
const DEFAULT_SECTIONS = {
  showHero: true, showAnnouncement: true, showQuickLinks: true,
  showPick: true, showRebuy: true, showConsume: true,
  zoneOrder: ["pick", "rebuy", "consume"],
};
const DEFAULT_LINKS = {
  brand: "/pages/index/index", topCart: "/pages/cart/cart",
  quickCategory: "/pages/category/category", quickCart: "/pages/cart/cart",
  pick: "/pages/zone/pick", rebuy: "/pages/zone/rebuy", consume: "/pages/zone/consume",
};
const LINK_TARGETS = new Set(["/pages/index/index", "/pages/category/category", "/pages/cart/cart",
  "/pages/zone/pick", "/pages/zone/rebuy", "/pages/zone/consume", "/pages/notice/notice"]);
function links(data) {
  const config = data && typeof data === "object" && !Array.isArray(data) ? data : {};
  const result = { ...DEFAULT_LINKS };
  for (const key of Object.keys(result)) if (LINK_TARGETS.has(config[key])) result[key] = config[key];
  return result;
}
function sections(data) {
  const config = data && typeof data === "object" && !Array.isArray(data) ? data : {};
  const result = { ...DEFAULT_SECTIONS };
  for (const key of ["showHero", "showAnnouncement", "showQuickLinks", "showPick", "showRebuy", "showConsume"])
    if (typeof config[key] === "boolean") result[key] = config[key];
  const order = config.zoneOrder;
  if (Array.isArray(order) && order.length === 3 &&
      new Set(order).size === 3 && order.every(key => ["pick", "rebuy", "consume"].includes(key)))
    result.zoneOrder = order;
  return result;
}
function zoneOrder(config) {
  const result = {};
  config.zoneOrder.forEach((key, index) => { result[key] = index + 1; });
  return result;
}

const defaults = {
  // 主宣传图叠加文字：与后台 ColumnImages 页面 hero 预设一致
  hero: [
    { text: "山水有约，好物相伴", visible: true, color: "#fff7e5", size: 26, bold: false, x: 5, y: 25, font: "song", spacing: 2, effect: "shadow" },
    { text: "品质好物 · 悦享生活", visible: true, color: "#fff7e5", size: 13, bold: false, x: 5, y: 47, font: "song", spacing: 2, effect: "shadow" },
  ],
  pick: [{ text: "自选区", visible: true, color: "#ffffff", size: 16, bold: true, x: 38, y: 18, font: "classic", spacing: 8, effect: "glow" }],
  rebuy: [{ text: "复购区", visible: true, color: "#ffffff", size: 14, bold: true, x: 64, y: 20, font: "hei", spacing: 1, effect: "none" }],
  consume: [{ text: "消费区", visible: true, color: "#ffffff", size: 14, bold: true, x: 7, y: 20, font: "hei", spacing: 1, effect: "shadow" }],
};
const fonts = {
  song: '"Songti SC","Noto Serif SC",serif',
  hei: '"PingFang SC","Microsoft YaHei",sans-serif',
  kai: '"STKaiti","KaiTi",serif',
  classic: 'Georgia,"Songti SC",serif',
};
const shadows = {
  none: "none",
  shadow: "0 2px 8px #172d3b99",
  glow: "0 0 8px #fff,0 2px 14px #ffffff99",
  outline: "1px 1px 0 #24372f,-1px -1px 0 #24372f,1px -1px 0 #24372f,-1px 1px 0 #24372f",
};

function number(value, fallback, min, max) {
  const n = Number(value);
  return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : fallback;
}
function captions(rows, fallback) {
  if (!Array.isArray(rows)) rows = fallback;
  return rows.filter(r => r && r.visible !== false && typeof r.text === "string" && r.text.trim()).slice(0, 8).map(r => {
    const x = number(r.x, 5, 0, 95), y = number(r.y, 20, 0, 95);
    const color = /^#[0-9a-f]{3,8}$/i.test(r.color || "") ? r.color : "#ffffff";
    return {
      text: r.text.slice(0, 200),
      style: "left:" + x + "%;top:" + y + "%;max-width:" + (100 - x) + "%;color:" + color +
        ";font-size:" + number(r.size, 14, 8, 80) + "px;font-weight:" + (r.bold ? "700" : "400") +
        ";font-family:" + (fonts[r.font] || fonts.song) +
        ";letter-spacing:" + number(r.spacing, 0, 0, 20) + "px;text-shadow:" +
        (shadows[r.effect] || shadows.shadow) + ";z-index:4;",
    };
  });
}
function fallbackCaptions() {
  const out = {};
  Object.keys(defaults).forEach(slot => { out[slot] = captions(defaults[slot], defaults[slot]); });
  return out;
}
function fallbackText() {
  // 首屏先用上一次后台文案，避免异步加载期间闪现内置默认文字
  let saved = null;
  try { saved = wx.getStorageSync(CACHE_KEY); } catch (e) {}
  const cached = saved && typeof saved === "object" && !Array.isArray(saved) ? saved : null;
  return Object.assign({}, DEFAULT_COPY, cached || {});
}

// 加时间戳，彻底绕开小程序端与 CDN 的缓存；nginx 对该目录也下发 no-store。
function get(path) {
  return new Promise(resolve => {
    wx.request({
      url: host + path + (path.indexOf("?") >= 0 ? "&" : "?") + "t=" + Date.now(),
      method: "GET",
      timeout: 8000,
      success: r => {
        if (r.statusCode !== 200) return resolve(null);
        let data = r.data;
        try { if (typeof data === "string") data = JSON.parse(data); } catch (e) { return resolve(null); }
        resolve(data);
      },
      fail: () => resolve(null),
    });
  });
}
function slides(data) {
  if (!Array.isArray(data)) return [hero];
  const rows = data.filter(p => typeof p === "string" && /^\/static\/festive-home\/hero(?:-[a-z0-9-]+)?\.jpg$/.test(p));
  return rows.length ? Array.from(new Set(rows)).map(p => host + p) : [hero];
}
function visibleText(rows, index) {
  if (!Array.isArray(rows)) return "";
  const row = rows[index];
  if (!row || row.visible === false || typeof row.text !== "string") return "";
  return row.text.trim().slice(0, 200);
}

// 文案优先级：home-copy.json 显式配置 > 后台「叠加文字」派生 > 内置兜底。
// 后台 UI 可编辑的 hero-captions / pick|rebuy|consume-captions 改字后立即联动。
function resolveText(copy, heroRows, pickRows, rebuyRows, consumeRows) {
  const cfg = copy && typeof copy === "object" && !Array.isArray(copy) ? copy : {};
  const out = Object.assign({}, DEFAULT_COPY, cfg);
  const has = key => Object.prototype.hasOwnProperty.call(cfg, key);
  const derive = (key, value) => { if (value && !has(key)) out[key] = value; };
  derive("heroTitle", visibleText(heroRows, 0));
  derive("heroSub", visibleText(heroRows, 1));
  const pickName = visibleText(pickRows, 1) || visibleText(pickRows, 0);
  const rebuyName = visibleText(rebuyRows, 0);
  const consumeName = visibleText(consumeRows, 0);
  if (pickName) {
    derive("pickHeading", pickName + "旅游活动");
    derive("pickBadge", pickName + "活动");
  }
  if (rebuyName) {
    derive("rebuyHeading", rebuyName + "推荐商品");
    derive("rebuyBadge", rebuyName);
  }
  if (consumeName) {
    derive("consumeHeading", consumeName + "推荐商品");
    derive("consumeBadge", consumeName);
  }
  return out;
}

let cached, loadedAt = 0;
function load(force) {
  if (!force && cached && Date.now() - loadedAt < 3000) return cached;
  loadedAt = Date.now();
  cached = Promise.all([
    get(STATIC + "hero-carousel.json"),
    get(STATIC + "hero-captions.json"),
    get(STATIC + "pick-captions.json"),
    get(STATIC + "rebuy-captions.json"),
    get(STATIC + "consume-captions.json"),
    get(STATIC + "home-copy.json"),
    get(STATIC + "home-layout.json"),
    get(STATIC + "home-links.json"),
  ]).then(rows => {
    const text = resolveText(rows[5], rows[1], rows[2], rows[3], rows[4]);
    try { wx.setStorageSync(CACHE_KEY, text); } catch (e) {}
    return {
      images: slides(rows[0]),
      captions: {
        hero: captions(rows[1], defaults.hero),
        pick: captions(rows[2], defaults.pick),
        rebuy: captions(rows[3], defaults.rebuy),
        consume: captions(rows[4], defaults.consume),
      },
      text,
      sections: sections(rows[6]),
      zoneOrder: zoneOrder(sections(rows[6])),
      links: links(rows[7]),
    };
  });
  return cached;
}
function layout() {
  let width = 375;
  try {
    const info = typeof wx.getWindowInfo === "function" ? wx.getWindowInfo() : wx.getSystemInfoSync();
    width = Number(info.windowWidth) || width;
  } catch (e) {}
  const wide = width >= 900;
  const card = Math.max(200, Math.min(width, 1200) - (wide ? 82 : 50));
  const gap = wide ? 16 : 9;
  return {
    pickHeight: Math.round(card / 2),
    rowWidth: Math.floor((card - gap) / 2),
    rowHeight: Math.round((card - gap) / 2 / 1.84),
    gap,
  };
}

exports.DEFAULT_COPY = DEFAULT_COPY;
exports.DEFAULT_SECTIONS = DEFAULT_SECTIONS;
exports.DEFAULT_LINKS = DEFAULT_LINKS;
exports.links = links;
exports.sections = sections;
exports.zoneOrder = zoneOrder;
exports.fallbackCaptions = fallbackCaptions;
exports.fallbackText = fallbackText;
exports.resolveText = resolveText;
exports.load = load;
exports.layout = layout;
exports.slides = slides;
exports.captions = captions;

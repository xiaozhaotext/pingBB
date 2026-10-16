/**
 * gen_admin_menu_mirror.mjs —— 生成 45_admin_menu_route_mirror.sql
 *
 * 背景：后台侧边栏 = 前端路由表（asyncRouterMap）按角色菜单过滤后的结果，过滤规则是
 * 「路由 name == ums_menu.name」。历史上同一批页面在不同环境用了不同的菜单 name
 * （desk / sysMgr …），名字对不上的路由会被整棵丢弃，于是后台会缺少
 * 后台桌面、系统设置、订单筛选、会员管理、财务钱包等一二级菜单。
 *
 * 生成规则：
 *   1. 结构 / 层级 / 标题 / 图标：取自 mall-admin-web/src/router/index.ts 的 asyncRouterMap（10 个一级分组）；
 *   2. hidden：线上已有同名菜单时以线上为准，否则取路由自带的 hidden；
 *   3. 排序：一级分组固定 pick > rebuy > consume > orderMgr > memberMgr > financeMgr > spendableWalletMgr > serviceMgr
 *      > serviceDesktop > systemSettings（消费区排在复购之后）；其余层级线上已有的按线上 sort 降序，
 *      线上没有的按路由声明顺序排在其后；
 *   4. 角色授权沿用 34 号脚本：持有「自选商品管理(pick)」的角色获得全部新菜单。
 *
 * 用法：
 *   node tools/gen_admin_menu_mirror.mjs [--router <路径>] [--out <路径>]
 * 默认读取 ../../../../../mall-admin-web/src/router/index.ts，输出 ../45_admin_menu_route_mirror.sql。
 * 只依赖 Node.js，无第三方依赖。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))

// 线上 GET /admin-api/admin/info 的菜单快照（仅保留本脚本会写入的 name），用于对齐线上排序与隐藏。
// 路由与线上不一致时以线上为准；如需刷新，请重新导出该接口的 menus 数组后替换本表。
const CLOUD_LOCK = {
  pick: {
    sort: 100,
    hidden: false
  },
  rebuy: {
    sort: 99,
    hidden: false
  },
  orderMgr: {
    sort: 98,
    hidden: false
  },
  memberMgr: {
    sort: 97,
    hidden: false
  },
  financeMgr: {
    sort: 96,
    hidden: false
  },
  serviceMgr: {
    sort: 95,
    hidden: false
  },
  serviceDesktop: {
    sort: 94,
    hidden: false
  },
  systemSettings: {
    sort: 93,
    hidden: false
  },
  financeAnalysis: {
    sort: 10,
    hidden: false
  },
  memberManagement: {
    sort: 9,
    hidden: false
  },
  orderMgrFilter: {
    sort: 10,
    hidden: false
  },
  pickCategory: {
    sort: 10,
    hidden: false
  },
  rebuyCategory: {
    sort: 10,
    hidden: false
  },
  serviceAccount: {
    sort: 10,
    hidden: false
  },
  serviceDesktopData: {
    sort: 12,
    hidden: false
  },
  systemManagementHome: {
    sort: 10,
    hidden: false
  },
  desktopClearCache: {
    sort: 11,
    hidden: false
  },
  financeFunds: {
    sort: 9,
    hidden: false
  },
  memberOperations: {
    sort: 8,
    hidden: false
  },
  orderSummary: {
    sort: 9,
    hidden: false
  },
  pickProduct: {
    sort: 9,
    hidden: false
  },
  rebuyProduct: {
    sort: 9,
    hidden: false
  },
  servicePrivacy: {
    sort: 9,
    hidden: false
  },
  systemDatabaseManagement: {
    sort: 8,
    hidden: false
  },
  financePoints: {
    sort: 8,
    hidden: false
  },
  orderMgrDetails: {
    sort: 8,
    hidden: false
  },
  serviceDesktopUser: {
    sort: 10,
    hidden: false
  },
  systemAdminSettings: {
    sort: 7,
    hidden: false
  },
  financeWalletManagement: {
    sort: 6,
    hidden: false
  },
  financeDividend: {
    sort: 5,
    hidden: false
  },
  financeProductPoints: {
    sort: 10,
    hidden: false
  },
  financeWallet: {
    sort: 10,
    hidden: false
  },
  memberMgrList: {
    sort: 11,
    hidden: false
  },
  memberMgrPartner: {
    sort: 12,
    hidden: true
  },
  serviceAdmin: {
    sort: 11,
    hidden: false
  },
  serviceDatabase: {
    sort: 10,
    hidden: false
  },
  serviceDesktopHome: {
    sort: 10,
    hidden: false
  },
  systemConfig: {
    sort: 10,
    hidden: false
  },
  financeCommissionAccounts: {
    sort: 9,
    hidden: false
  },
  financeRepurchasePoints: {
    sort: 9,
    hidden: false
  },
  memberIdentity: {
    sort: 10,
    hidden: true
  },
  memberMgrCofounder: {
    sort: 11,
    hidden: true
  },
  serviceDepartment: {
    sort: 7,
    hidden: false
  },
  serviceDesktopPassword: {
    sort: 9,
    hidden: false
  },
  systemDatabaseBackups: {
    sort: 9,
    hidden: false
  },
  systemImages: {
    sort: 9,
    hidden: false
  },
  desktopLock: {
    sort: 8,
    hidden: false
  },
  financeCommissionLedger: {
    sort: 8,
    hidden: false
  },
  memberStats: {
    sort: 8,
    hidden: false
  },
  serviceLoginLog: {
    sort: 6,
    hidden: false
  },
  systemAddresses: {
    sort: 8,
    hidden: false
  },
  desktopLogout: {
    sort: 7,
    hidden: false
  },
  financeCommissionSources: {
    sort: 7,
    hidden: true
  },
  memberMgrNotices: {
    sort: 8,
    hidden: true
  },
  systemVisits: {
    sort: 7,
    hidden: false
  },
  memberRegisterNotice: {
    sort: 7,
    hidden: false
  },
  serviceBrand: {
    sort: 6,
    hidden: true
  },
  systemMessages: {
    sort: 5,
    hidden: false
  },
  serviceProductAttr: {
    sort: 5,
    hidden: true
  },
  serviceCoupon: {
    sort: 4,
    hidden: true
  },
  systemLogs: {
    sort: 3,
    hidden: false
  },
  serviceFlash: {
    sort: 3,
    hidden: true
  },
  serviceAdvertise: {
    sort: 2,
    hidden: true
  },
  serviceHomeBrand: {
    sort: 1,
    hidden: true
  },
  serviceHomeNew: {
    sort: 0,
    hidden: true
  },
  serviceHomeHot: {
    sort: -1,
    hidden: true
  },
  serviceHomeSubject: {
    sort: -2,
    hidden: true
  },
  systemSiteProfile: {
    sort: 10,
    hidden: false
  },
  systemParameters: {
    sort: 9,
    hidden: false
  },
  systemWatermark: {
    sort: 8,
    hidden: false
  },
  systemUpload: {
    sort: 7,
    hidden: false
  },
  systemBackendDirectory: {
    sort: 6,
    hidden: false
  },
  systemIpFilter: {
    sort: 5,
    hidden: false
  },
  systemSms: {
    sort: 4,
    hidden: false
  },
  systemWechat: {
    sort: 3,
    hidden: false
  }
};

const ROOTS = ['pick', 'rebuy', 'consume', 'orderMgr', 'memberMgr', 'financeMgr', 'spendableWalletMgr', 'serviceMgr', 'serviceDesktop', 'systemSettings']
// 没有 meta 的跳转型路由，给一个占位标题，避免写入空标题
const TITLE_FALLBACK = { orderMgrLegacyList: '订单列表（旧入口）' }
// 已被新控制台取代的旧菜单 name：只隐藏，不删除记录与授权
const LEGACY_NAMES = ['desk', 'sysMgr', 'deskDashboard', 'deskClearCache', 'deskUser', 'deskUserHome', 'deskUserPassword', 'deskUserLock', 'deskUserLogout', 'sysHome', 'sysAdmin', 'sysDatabase', 'sysExtra', 'sysConfig', 'sysAreaLibrary', 'sysMessageRecords', 'sysVisitStatistics', 'sysLogManager', 'sysImages', 'sysDepartment', 'sysAdminUsers', 'sysRoles', 'sysMenus', 'sysLoginLog', 'sysDatabaseBackup', 'sysDatabaseBackupList', 'sysNotices', 'sysBrand', 'sysProductAttr', 'sysCoupon', 'sysFlash', 'sysAdvertise', 'sysRecommend', 'orderMgrList', 'orderDetailView', 'memberManage', 'memberOperation', 'financeWallet']

const args = process.argv.slice(2)
const readArg = (flag, fallback) => { const i = args.indexOf(flag); return i >= 0 && args[i + 1] ? args[i + 1] : fallback }
const routerPath = readArg('--router', path.resolve(here, '../../../../../mall-admin-web/src/router/index.ts'))
const outPath = readArg('--out', path.resolve(here, '../45_admin_menu_route_mirror.sql'))

/** 解析 asyncRouterMap：用括号深度还原父子关系 */
function parseRoutes(file) {
  const src = fs.readFileSync(file, 'utf8')
  const anchor = src.match(/export const asyncRouterMap[^=]*=(\s*)\[/)
  if (!anchor) throw new Error('未找到 asyncRouterMap：' + file)
  const start = anchor.index + anchor[0].length - 1
  let depth = 0, end = start
  for (let i = start; i < src.length; i++) {
    const c = src[i]
    if (c === '[' || c === '{') depth++
    else if (c === ']' || c === '}') { depth--; if (depth === 0) { end = i + 1; break } }
  }
  const lines = src.slice(start, end).split(/\r?\n/)
  const entries = []
  const stack = []
  let d = 0
  for (let li = 0; li < lines.length; li++) {
    const line = lines[li]
    const nameM = line.match(/name:\s*'([^']+)'/)
    if (nameM) {
      while (stack.length && stack[stack.length - 1].depth >= d) stack.pop()
      entries.push({ name: nameM[1], parent: stack.length ? stack[stack.length - 1].name : null, title: null, icon: null, hidden: false, line: li })
      stack.push({ name: nameM[1], depth: d })
    }
    d += (line.match(/[{[]/g) || []).length - (line.match(/[}\]]/g) || []).length
  }
  // meta 可能跨行，取本条目到下一条目之间的第一个 title / icon
  for (let k = 0; k < entries.length; k++) {
    const e = entries[k]
    const to = k + 1 < entries.length ? entries[k + 1].line : lines.length
    for (let li = e.line; li < to; li++) {
      const t = lines[li].match(/title:\s*'([^']+)'/)
      const ic = lines[li].match(/icon:\s*'([^']+)'/)
      if (t && e.title === null) e.title = t[1]
      if (ic && e.icon === null) e.icon = ic[1]
      if (/hidden:\s*true/.test(lines[li])) e.hidden = true
    }
  }
  return entries
}

function buildRows(entries) {
  const keep = new Set()
  for (const root of ROOTS) (function walk(name) { keep.add(name); for (const e of entries) if (e.parent === name) walk(e.name) })(root)
  const rows = entries.filter(e => keep.has(e.name))
  const byName = new Map(rows.map(r => [r.name, r]))
  for (const r of rows) {
    r.title = r.title || TITLE_FALLBACK[r.name] || r.name
    let level = 0, parent = r.parent
    while (parent) { level++; parent = byName.get(parent).parent }
    r.level = level
    const lock = CLOUD_LOCK[r.name]
    if (lock) {
      if (lock.title && lock.title !== r.name) r.title = lock.title
      if (lock.icon) r.icon = lock.icon
      r.hidden = !!lock.hidden
      r.cloudSort = lock.sort
    }
  }
  ROOTS.forEach((name, i) => { const r = byName.get(name); if (r) r.sort = name === 'spendableWalletMgr' ? 945 : 1000 - (i > 6 ? i - 1 : i) * 10 })
  for (const parentName of new Set(rows.map(r => r.parent))) {
    if (!parentName) continue
    const sibs = rows.filter(r => r.parent === parentName)
    const withCloud = sibs.filter(s => s.cloudSort !== undefined).sort((a, b) => b.cloudSort - a.cloudSort)
    withCloud.concat(sibs.filter(s => s.cloudSort === undefined)).forEach((s, i) => { s.sort = 100 - i * 3 })
  }
  rows.sort((a, b) => a.level - b.level || b.sort - a.sort || a.name.localeCompare(b.name))
  return rows
}

function renderSql(rows) {
  const q = v => v === null || v === undefined ? 'NULL' : "'" + String(v).replace(/'/g, "''") + "'"
  const L = []
  L.push('-- 45_admin_menu_route_mirror.sql')
  L.push('--')
  L.push('-- 背景：后台侧边栏由前端路由表过滤生成，权限过滤按「路由 name == ums_menu.name」匹配，')
  L.push('-- 匹配不到且非 hidden 的路由会被整棵丢弃。历史迁移（33/34/35/41）在不同环境里给同一批页面用了')
  L.push('-- 不同的菜单 name（desk/sysMgr 等），导致本地后台缺少 后台桌面、系统设置、订单筛选、会员管理、')
  L.push('-- 财务钱包 等一二级菜单，与线上不一致。')
  L.push('--')
  L.push('-- 本脚本把 ums_menu 与前端路由树（mall-admin-web/src/router/index.ts 的 asyncRouterMap）按 name 对齐：')
  L.push('--   * 结构 / 标题 / 图标 / hidden 以当前前端路由为准；')
  L.push('--   * 已上线菜单的排序与隐藏以线上 /admin-api/admin/info 为准，保证与线上顺序一致；')
  L.push('--   * 10 个一级分组的顺序：自选商品管理 > 复购商品管理 > 消费区商品管理 > 订单管理 > 会员 > 财务中心 > 消费余额与充值 > 服务中心 > 后台桌面 > 系统设置。')
  L.push('--')
  L.push('-- 幂等：按 name 只补缺失行、只归一已有行元数据、只补缺失授权，不删除菜单、角色授权或业务数据。')
  L.push('-- 本文件由 tools/gen_admin_menu_mirror.mjs 生成，路由调整后请重新生成，不要手工改。')
  L.push('-- 执行：mysql --default-character-set=utf8mb4 -u<user> -p<pass> <database> < 45_admin_menu_route_mirror.sql')
  L.push('')
  L.push('START TRANSACTION;')
  L.push('')
  L.push('CREATE TEMPORARY TABLE tmp_admin_menu_mirror (')
  L.push('  name   VARCHAR(100) NOT NULL,')
  L.push('  parent VARCHAR(100) NULL,')
  L.push('  title  VARCHAR(100) NOT NULL,')
  L.push('  icon   VARCHAR(200) NULL,')
  L.push('  level  INT NOT NULL,')
  L.push('  sort   INT NOT NULL,')
  L.push('  hidden TINYINT NOT NULL,')
  L.push('  PRIMARY KEY (name)')
  L.push(') ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;')
  L.push('')
  L.push('INSERT INTO tmp_admin_menu_mirror(name, parent, title, icon, level, sort, hidden) VALUES')
  L.push(rows.map(r => `  (${q(r.name)}, ${q(r.parent)}, ${q(r.title)}, ${q(r.icon)}, ${r.level}, ${r.sort}, ${r.hidden ? 1 : 0})`).join(',\n') + ';')
  L.push('')
  L.push('-- 1) 补齐缺失菜单')
  L.push('INSERT INTO ums_menu(parent_id, create_time, title, level, sort, name, icon, hidden)')
  L.push('SELECT 0, NOW(), t.title, t.level, t.sort, t.name, t.icon, t.hidden')
  L.push('FROM tmp_admin_menu_mirror t')
  L.push('LEFT JOIN ums_menu m ON m.name = t.name')
  L.push('WHERE m.id IS NULL;')
  L.push('')
  L.push('-- 2) 归一已有菜单的标题/图标/排序/隐藏/层级')
  L.push('UPDATE ums_menu m')
  L.push('JOIN tmp_admin_menu_mirror t ON t.name = m.name')
  L.push('SET m.title = t.title, m.icon = t.icon, m.sort = t.sort, m.hidden = t.hidden, m.level = t.level;')
  L.push('')
  L.push('-- 3) 修正父子关系（一级菜单 parent_id = 0）')
  L.push('UPDATE ums_menu m')
  L.push('JOIN tmp_admin_menu_mirror t ON t.name = m.name')
  L.push('LEFT JOIN ums_menu p ON p.name = t.parent')
  L.push('SET m.parent_id = COALESCE(p.id, 0);')
  L.push('')
  L.push('-- 4) 给已开通新控制台的角色补齐授权（与 34 号脚本一致：持有「自选商品管理」的角色获得全部新菜单）')
  L.push('INSERT INTO ums_role_menu_relation(role_id, menu_id)')
  L.push('SELECT DISTINCT r.role_id, m.id')
  L.push('FROM ums_role_menu_relation r')
  L.push("JOIN ums_menu root ON root.id = r.menu_id AND root.name = 'pick'")
  L.push('JOIN ums_menu m ON m.name IN (SELECT name FROM tmp_admin_menu_mirror)')
  L.push('LEFT JOIN ums_role_menu_relation old ON old.role_id = r.role_id AND old.menu_id = m.id')
  L.push('WHERE old.menu_id IS NULL;')
  L.push('')
  // 与规范菜单同名的旧 name（如 financeWallet）不参与隐藏，避免误伤现用菜单
  const legacyOnly = LEGACY_NAMES.filter(n => !rows.some(r => r.name === n))
  L.push('-- 5) 隐藏被新控制台取代的旧菜单记录（保留记录与授权，仅不再展示；与规范菜单同名的旧记录不在此列）')
  L.push('UPDATE ums_menu SET hidden = 1 WHERE name IN (' + legacyOnly.map(n => "'" + n + "'").join(', ') + ');')
  L.push('')
  L.push('DROP TEMPORARY TABLE tmp_admin_menu_mirror;')
  L.push('')
  L.push('COMMIT;')
  return L.join('\n') + '\n'
}

const rows = buildRows(parseRoutes(routerPath))
fs.writeFileSync(outPath, renderSql(rows))
console.log(`生成 ${rows.length} 条菜单 -> ${outPath}`)

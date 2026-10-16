import { createRouter, createWebHashHistory, RouterView } from 'vue-router'
import Layout from '@/views/layout/Layout.vue'
import type { RouteRecordExt } from '@/types/router'

export const constantRouterMap: RouteRecordExt[] = [
  { path: '/', redirect: '/home', hidden: true },
  { path: '/404', component: () => import('@/views/normal/404/index.vue'), hidden: true },
  { path: '/login', component: () => import('@/views/normal/login/index.vue'), hidden: true },
  {
    path: '/home',
    component: Layout,
    name: 'home',
    hidden: true,
    meta: { title: '大数据界面', icon: 'dashboard' },
    children: [
      { path: '', name: 'homeDashboard', component: () => import('@/views/home/LiveDashboard.vue'), hidden: true, meta: { title: '大数据界面' } },
    ],
  },
]

export const asyncRouterMap: RouteRecordExt[] = [
  // ===== 后台一级功能分组（对齐原图思维导图） =====

  // 1 自选商品管理
  {
    path: '/pick', component: Layout, redirect: '/pick/category', name: 'pick', alwaysShow: true,
    meta: { title: '自选商品管理', icon: 'product' },
    children: [
      { path: 'category', name: 'pickCategory', component: () => import('@/views/pms/productCate/index.vue'), meta: { title: '分类管理', icon: 'product-cate' } },
      { path: 'product', name: 'pickProduct', component: () => import('@/views/zone/ZoneProductList.vue'), meta: { title: '商品列表', icon: 'product-list' } },
      { path: 'add-category', name: 'pickAddCategory', component: () => import('@/views/pms/productCate/add.vue'), meta: { title: '添加商品分类' }, hidden: true },
      { path: 'update-category', name: 'pickUpdateCategory', component: () => import('@/views/pms/productCate/update.vue'), meta: { title: '编辑商品分类' }, hidden: true },
      { path: 'add-product', name: 'pickAddProduct', component: () => import('@/views/pms/product/add.vue'), meta: { title: '添加商品' }, hidden: true },
      { path: 'update-product', name: 'pickUpdateProduct', component: () => import('@/views/pms/product/update.vue'), meta: { title: '编辑商品' }, hidden: true },
    ],
  },
  // 2 复购商品管理
  {
    path: '/rebuy', component: Layout, redirect: '/rebuy/category', name: 'rebuy', alwaysShow: true,
    meta: { title: '复购商品管理', icon: 'product-list' },
    children: [
      { path: 'category', name: 'rebuyCategory', component: () => import('@/views/pms/productCate/index.vue'), meta: { title: '分类管理', icon: 'product-cate' } },
      { path: 'product', name: 'rebuyProduct', component: () => import('@/views/zone/ZoneProductList.vue'), meta: { title: '商品列表', icon: 'product-list' } },
      { path: 'add-category', name: 'rebuyAddCategory', component: () => import('@/views/pms/productCate/add.vue'), meta: { title: '添加商品分类' }, hidden: true },
      { path: 'update-category', name: 'rebuyUpdateCategory', component: () => import('@/views/pms/productCate/update.vue'), meta: { title: '编辑商品分类' }, hidden: true },
      { path: 'add-product', name: 'rebuyAddProduct', component: () => import('@/views/pms/product/add.vue'), meta: { title: '添加商品' }, hidden: true },
      { path: 'update-product', name: 'rebuyUpdateProduct', component: () => import('@/views/pms/product/update.vue'), meta: { title: '编辑商品' }, hidden: true },
    ],
  },
  // 3 消费区商品管理
  {
    path: '/consume', component: Layout, redirect: '/consume/category', name: 'consume', alwaysShow: true,
    meta: { title: '消费区商品管理', icon: 'product-list' },
    children: [
      { path: 'category', name: 'consumeCategory', component: () => import('@/views/pms/productCate/index.vue'), meta: { title: '分类管理', icon: 'product-cate' } },
      { path: 'product', name: 'consumeProduct', component: () => import('@/views/zone/ZoneProductList.vue'), meta: { title: '商品列表', icon: 'product-list' } },
      { path: 'add-category', name: 'consumeAddCategory', component: () => import('@/views/pms/productCate/add.vue'), meta: { title: '添加商品分类' }, hidden: true },
      { path: 'update-category', name: 'consumeUpdateCategory', component: () => import('@/views/pms/productCate/update.vue'), meta: { title: '编辑商品分类' }, hidden: true },
      { path: 'add-product', name: 'consumeAddProduct', component: () => import('@/views/pms/product/add.vue'), meta: { title: '添加商品' }, hidden: true },
      { path: 'update-product', name: 'consumeUpdateProduct', component: () => import('@/views/pms/product/update.vue'), meta: { title: '编辑商品' }, hidden: true },
    ],
  },
  // 4 订单管理
  {
    path: '/orderMgr', component: Layout, redirect: '/orderMgr/filter', name: 'orderMgr', alwaysShow: true,
    meta: { title: '订单管理', icon: 'order' },
    children: [
      { path: 'filter', name: 'orderMgrFilter', component: () => import('@/views/oms/order/index.vue'), meta: { title: '筛选', icon: 'product-list' } },
      { path: 'list', name: 'orderMgrLegacyList', redirect: '/orderMgr/filter', hidden: true },
      { path: 'summary', name: 'orderSummary', component: () => import('@/views/order/OrderSummary.vue'), meta: { title: '订单销量汇总', icon: 'total-today' } },
      { path: 'details', name: 'orderMgrDetails', component: () => import('@/views/order/OrderDetails.vue'), meta: { title: '订单详情', icon: 'document' } },
      { path: 'orderDetail', name: 'orderMgrOrderDetail', component: () => import('@/views/oms/order/orderDetail.vue'), meta: { title: '订单详情', icon: 'document' }, hidden: true },
      { path: 'returnApplyDetail', name: 'orderMgrReturnApplyDetail', component: () => import('@/views/oms/apply/applyDetail.vue'), meta: { title: '售后详情' }, hidden: true },
      { path: 'deliverOrderList', name: 'orderMgrDeliverOrderList', component: () => import('@/views/oms/order/deliverOrderList.vue'), meta: { title: '订单发货' }, hidden: true },
    ],
  },
  // 5 会员
  {
    path: '/memberMgr', component: Layout, redirect: '/memberMgr/management/list', name: 'memberMgr', alwaysShow: true,
    meta: { title: '会员', icon: 'ums-admin' },
    children: [
      { path: 'management', name: 'memberManagement', component: RouterView, redirect: '/memberMgr/management/list', meta: { title: '会员管理', icon: 'ums-role' }, children: [
        { path: 'list', name: 'memberMgrList', component: () => import('@/views/p0/P0Workbench.vue'), meta: { title: '会员列表', icon: 'user', section: 'p0Members' } },
        { path: 'identity', name: 'memberIdentity', component: () => import('@/views/member/MemberIdentity.vue'), hidden: true, meta: { title: '会员身份', icon: 'ums-role' } },
      ] },
      { path: 'operations', name: 'memberOperations', component: RouterView, redirect: '/memberMgr/operations/stats', meta: { title: '会员操作', icon: 'form' }, children: [
        { path: 'partner', name: 'memberMgrPartner', component: () => import('@/views/p0/P0Workbench.vue'), meta: { title: '合伙人管理', icon: 'ums-role', section: 'p0Partner' } },
        { path: 'cofounder', name: 'memberMgrCofounder', component: () => import('@/views/p0/P0Workbench.vue'), meta: { title: '联创管理', icon: 'ums-role', section: 'p0Cofounder' } },
        { path: 'stats', name: 'memberStats', component: () => import('@/views/member/MemberStats.vue'), meta: { title: '会员统计', icon: 'total-today' } },
        { path: 'register-notice', name: 'memberRegisterNotice', component: () => import('@/views/member/RegisterNotice.vue'), meta: { title: '注册须知', icon: 'form' } },
        { path: 'notices', name: 'memberMgrNotices', component: () => import('@/views/p0/Operations.vue'), meta: { title: '公告管理', icon: 'form', section: 'p0Notices' } },
        { path: 'consultations', name: 'memberMgrConsultations', component: () => import('@/views/p0/Operations.vue'), meta: { title: '在线咨询', icon: 'form', section: 'p0Consultations' } },
        { path: 'message-records', name: 'memberMgrMessageRecords', component: () => import('@/views/p0/MessageRecords.vue'), meta: { title: '发送记录', icon: 'form' } },
      ] },
    ],
  },
  // 6 财务中心
  {
    path: '/financeMgr', component: Layout, redirect: '/financeMgr/analysis', name: 'financeMgr', alwaysShow: true,
    meta: { title: '财务中心', icon: 'total-today' },
    children: [
      { path: 'analysis', name: 'financeAnalysis', component: () => import('@/views/finance/FundAnalysis.vue'), meta: { title: '资金分析', icon: 'total-today' } },
      { path: 'funds', name: 'financeFunds', component: () => import('@/views/finance/FundOverview.vue'), meta: { title: '资金管理', icon: 'total-today' } },
      { path: 'points', name: 'financePoints', component: RouterView, redirect: '/financeMgr/points/product', meta: { title: '积分明细', icon: 'product-comment' }, children: [
        { path: 'product', name: 'financeProductPoints', component: () => import('@/views/finance/PointsDetail.vue'), meta: { title: '产品积分明细', icon: 'product-comment' } },
        { path: 'repurchase', name: 'financeRepurchasePoints', component: () => import('@/views/finance/PointsDetail.vue'), meta: { title: '复购积分明细', icon: 'product-comment' } },
      ] },
      { path: 'wallet', name: 'financeWalletManagement', component: RouterView, redirect: '/financeMgr/wallet/withdrawals', meta: { title: '钱包明细管理', icon: 'total-week' }, children: [
        { path: 'withdrawals', name: 'financeWallet', component: () => import('@/views/p0/Finance.vue'), meta: { title: '佣金提现审核', icon: 'total-week', section: 'withdraws' } },
        { path: 'accounts', name: 'financeCommissionAccounts', component: () => import('@/views/p0/Finance.vue'), meta: { title: '佣金账户', icon: 'total-week', section: 'accounts' } },
        { path: 'ledger', name: 'financeCommissionLedger', component: () => import('@/views/p0/Finance.vue'), meta: { title: '佣金明细', icon: 'document', section: 'ledger' } },
        { path: 'sources', name: 'financeCommissionSources', component: () => import('@/views/p0/Finance.vue'), meta: { title: '佣金来源', icon: 'document', section: 'byType' } },
      ] },
      { path: 'dividend', name: 'financeDividend', component: () => import('@/views/p0/P0Workbench.vue'), meta: { title: '每日贡献奖励池', icon: 'total-yesterday', section: 'p0Dividend' } },
    ],
  },
    {
      path: '/dividendPools', component: Layout, redirect: '/dividendPools/partner', name: 'dividendPoolMgr', alwaysShow: true,
      meta: { title: '分红池', icon: 'total-yesterday' },
      children: [
        { path: 'partner', alias: '/financeMgr/partner-pool', name: 'financePartnerPool', component: () => import('@/views/finance/TriPools.vue'), meta: { title: '合伙人池', pool: 'PARTNER', icon: 'total-yesterday' } },
        { path: 'cofounder', alias: '/financeMgr/cofounder-pool', name: 'financeCofounderPool', component: () => import('@/views/finance/TriPools.vue'), meta: { title: '联创池', pool: 'COFOUNDER', icon: 'total-yesterday' } },
        { path: 'operations', alias: '/financeMgr/operations-pool', name: 'financeOperationsPool', component: () => import('@/views/finance/TriPools.vue'), meta: { title: '运营池', pool: 'OPERATIONS', icon: 'total-yesterday' } },
      ],
    },
    // 7 服务中心
  {
    path: '/serviceMgr', component: Layout, redirect: '/serviceMgr/account', name: 'serviceMgr', alwaysShow: true,
    meta: { title: '服务中心', icon: 'ums' },
    children: [
      { path: 'account', name: 'serviceAccount', component: () => import('@/views/account/Account.vue'), meta: { title: '账号与安全', icon: 'password' } },
      { path: 'privacy', name: 'servicePrivacy', component: () => import('@/views/p0/SiteProfile.vue'), meta: { title: '隐私政策', icon: 'form' } },
    ],
  },

  // 后台桌面与服务中心同为一级菜单。
  {
    path: '/desktop', component: Layout, redirect: '/desktop/big-data', name: 'serviceDesktop', alwaysShow: true,
    meta: { title: '后台桌面', icon: 'dashboard' },
    children: [
      { path: 'big-data', name: 'serviceDesktopData', component: () => import('@/views/home/LiveDashboard.vue'), meta: { title: '大数据界面', icon: 'dashboard' } },
      { path: 'cache-clear', name: 'desktopClearCache', component: () => import('@/views/p0/SystemSettings.vue'), meta: { title: '清除缓存', icon: 'refresh', section: 'cache' } },
      { path: 'user', name: 'serviceDesktopUser', component: RouterView, redirect: '/desktop/user/system-home', meta: { title: '用户', icon: 'user' }, children: [
        { path: 'system-home', name: 'serviceDesktopHome', component: () => import('@/views/home/LiveDashboard.vue'), meta: { title: '系统首页', icon: 'dashboard' } },
        { path: 'account', name: 'serviceDesktopPassword', component: () => import('@/views/account/Account.vue'), meta: { title: '修改密码', icon: 'password' } },
        { path: 'lock', name: 'desktopLock', component: () => import('@/views/normal/DesktopAction.vue'), meta: { title: '系统锁屏', icon: 'lock', desktopAction: 'lock' } },
        { path: 'logout', name: 'desktopLogout', component: () => import('@/views/normal/DesktopAction.vue'), meta: { title: '退出系统', icon: 'logout', desktopAction: 'logout' } },
      ] },
    ],
  },

  // 8 系统设置：管理首页、管理员设置、数据库管理
  {
    path: '/systemSettings', component: Layout, redirect: '/systemSettings/management/config/parameters', name: 'systemSettings', alwaysShow: true,
    meta: { title: '系统设置', icon: 'ums' },
    children: [
      { path: 'management', name: 'systemManagementHome', component: RouterView, redirect: '/systemSettings/management/config/parameters', meta: { title: '管理首页', icon: 'dashboard' }, children: [
        { path: 'config', name: 'systemConfig', component: RouterView, redirect: '/systemSettings/management/config/parameters', meta: { title: '系统配置', icon: 'ums' }, children: [
          { path: 'parameters', name: 'systemParameters', component: () => import('@/views/p0/P0Workbench.vue'), meta: { title: '系统参数', icon: 'form', section: 'p0Settings' } },
          { path: 'company', name: 'systemSiteProfile', component: () => import('@/views/p0/SiteProfile.vue'), meta: { title: '公司信息', icon: 'form' } },
          { path: 'watermark', name: 'systemWatermark', component: () => import('@/views/p0/SystemSettings.vue'), meta: { title: '水印设置', icon: 'form', section: 'watermark' } },
          { path: 'upload', name: 'systemUpload', component: () => import('@/views/p0/SystemSettings.vue'), meta: { title: '上传设置', icon: 'form', section: 'upload' } },
          { path: 'backend-directory', name: 'systemBackendDirectory', component: () => import('@/views/p0/SystemSettings.vue'), meta: { title: '后台目录', icon: 'form', section: 'upload' } },
          { path: 'ip-filter', name: 'systemIpFilter', component: () => import('@/views/p0/SystemSettings.vue'), meta: { title: 'IP 过滤设置', icon: 'form', section: 'ip_access' } },
          { path: 'sms', name: 'systemSms', component: () => import('@/views/p0/P0Workbench.vue'), meta: { title: '短信设置', icon: 'form', section: 'p0SmsSettings' } },
          { path: 'wechat', name: 'systemWechat', component: () => import('@/views/p0/SystemSettings.vue'), meta: { title: '微信设置', icon: 'form', section: 'miniapp' } },
        ] },
        { path: 'images', name: 'systemImages', component: () => import('@/views/p0/ImageManager.vue'), meta: { title: '图片管理', icon: 'eye' } },
        { path: 'addresses', name: 'systemAddresses', component: () => import('@/views/p0/AreaLibrary.vue'), meta: { title: '地址库管理', icon: 'marker' } },
        { path: 'messages', name: 'systemMessages', component: () => import('@/views/p0/MessageRecords.vue'), meta: { title: '发送记录', icon: 'form' } },
        { path: 'visits', name: 'systemVisits', component: () => import('@/views/p0/VisitStatistics.vue'), meta: { title: '访问统计', icon: 'dashboard' } },
        { path: 'logs', name: 'systemLogs', component: () => import('@/views/p0/LoginLog.vue'), meta: { title: '日志管理', icon: 'document' } },
        { path: 'brand', name: 'serviceBrand', component: () => import('@/views/pms/brand/index.vue'), meta: { title: '品牌管理', icon: 'product-brand' } },
        { path: 'product-attr', name: 'serviceProductAttr', component: () => import('@/views/pms/productAttr/index.vue'), meta: { title: '商品属性', icon: 'product-attr' } },
        { path: 'coupon', name: 'serviceCoupon', component: () => import('@/views/sms/coupon/index.vue'), meta: { title: '优惠券', icon: 'sms-coupon' } },
        { path: 'flash', name: 'serviceFlash', component: () => import('@/views/sms/flash/index.vue'), meta: { title: '秒杀活动', icon: 'sms-flash' } },
        { path: 'advertise', name: 'serviceAdvertise', component: () => import('@/views/sms/advertise/index.vue'), meta: { title: '广告列表', icon: 'sms-ad' } },
        { path: 'home-brand', name: 'serviceHomeBrand', component: () => import('@/views/sms/brand/index.vue'), meta: { title: '品牌推荐', icon: 'product-brand' } },
        { path: 'home-new', name: 'serviceHomeNew', component: () => import('@/views/sms/new/index.vue'), meta: { title: '新品推荐', icon: 'sms-new' } },
        { path: 'home-hot', name: 'serviceHomeHot', component: () => import('@/views/sms/hot/index.vue'), meta: { title: '人气推荐', icon: 'sms-hot' } },
        { path: 'home-subject', name: 'serviceHomeSubject', component: () => import('@/views/sms/subject/index.vue'), meta: { title: '专题推荐', icon: 'sms-subject' } },
      ] },
      { path: 'admins', name: 'systemAdminSettings', component: RouterView, redirect: '/systemSettings/admins/departments', meta: { title: '管理员设置', icon: 'ums-admin' }, children: [
        { path: 'departments', name: 'serviceDepartment', component: () => import('@/views/p0/Department.vue'), meta: { title: '部门管理', icon: 'ums' } },
        { path: 'users', name: 'serviceAdmin', component: () => import('@/views/ums/admin/index.vue'), meta: { title: '管理用户', icon: 'ums-admin' } },
        { path: 'login-log', name: 'serviceLoginLog', component: () => import('@/views/p0/LoginLog.vue'), meta: { title: '登录日志', icon: 'document' } },
      ] },
      { path: 'database', name: 'systemDatabaseManagement', component: RouterView, redirect: '/systemSettings/database/tables', meta: { title: '数据库管理', icon: 'table' }, children: [
        { path: 'tables', name: 'serviceDatabase', component: () => import('@/views/p0/Database.vue'), meta: { title: '数据库备份', icon: 'table', section: 'tables' } },
        { path: 'backups', name: 'systemDatabaseBackups', component: () => import('@/views/p0/Database.vue'), meta: { title: '查看备份', icon: 'document', section: 'backups' } },
      ] },
    ],
  },

  {
    path: '/pms',
    component: Layout,
    redirect: '/pms/product',
    name: 'pms',
    meta: { title: '商品', icon: 'product' },
    children: [
      {
        path: 'product',
        name: 'product',
        component: () => import('@/views/pms/product/index.vue'),
        meta: { title: '商品列表', icon: 'product-list' },
      },
      {
        path: 'addProduct',
        name: 'addProduct',
        component: () => import('@/views/pms/product/add.vue'),
        meta: { title: '添加商品', icon: 'product-add' },
      },
      {
        path: 'updateProduct',
        name: 'updateProduct',
        component: () => import('@/views/pms/product/update.vue'),
        meta: { title: '修改商品', icon: 'product-add' },
        hidden: true,
      },
      {
        path: 'zones',
        name: 'productZones',
        component: () => import('@/views/pms/zones/index.vue'),
        meta: { title: '专区商品管理', icon: 'product-list' },
      },
      {
        path: 'productCate',
        name: 'productCate',
        component: () => import('@/views/pms/productCate/index.vue'),
        meta: { title: '商品分类', icon: 'product-cate' },
      },
      {
        path: 'addProductCate',
        name: 'addProductCate',
        component: () => import('@/views/pms/productCate/add.vue'),
        meta: { title: '添加商品分类' },
        hidden: true,
      },
      {
        path: 'updateProductCate',
        name: 'updateProductCate',
        component: () => import('@/views/pms/productCate/update.vue'),
        meta: { title: '修改商品分类' },
        hidden: true,
      },
      {
        path: 'productAttr',
        name: 'productAttr',
        component: () => import('@/views/pms/productAttr/index.vue'),
        meta: { title: '商品类型', icon: 'product-attr' },
      },
      {
        path: 'productAttrList',
        name: 'productAttrList',
        component: () => import('@/views/pms/productAttr/productAttrList.vue'),
        meta: { title: '商品属性列表' },
        hidden: true,
      },
      {
        path: 'addProductAttr',
        name: 'addProductAttr',
        component: () => import('@/views/pms/productAttr/addProductAttr.vue'),
        meta: { title: '添加商品属性' },
        hidden: true,
      },
      {
        path: 'updateProductAttr',
        name: 'updateProductAttr',
        component: () => import('@/views/pms/productAttr/updateProductAttr.vue'),
        meta: { title: '修改商品属性' },
        hidden: true,
      },
      {
        path: 'brand',
        name: 'brand',
        component: () => import('@/views/pms/brand/index.vue'),
        meta: { title: '品牌管理', icon: 'product-brand' },
      },
      {
        path: 'addBrand',
        name: 'addBrand',
        component: () => import('@/views/pms/brand/add.vue'),
        meta: { title: '添加品牌' },
        hidden: true,
      },
      {
        path: 'updateBrand',
        name: 'updateBrand',
        component: () => import('@/views/pms/brand/update.vue'),
        meta: { title: '编辑品牌' },
        hidden: true,
      },
    ],
  },
  {
    path: '/oms',
    component: Layout,
    redirect: '/oms/order',
    name: 'oms',
    meta: { title: '订单', icon: 'order' },
    children: [
      {
        path: 'order',
        name: 'order',
        component: () => import('@/views/oms/order/index.vue'),
        meta: { title: '订单列表', icon: 'product-list' },
      },
      {
        path: 'orderDetail',
        name: 'orderDetail',
        component: () => import('@/views/oms/order/orderDetail.vue'),
        meta: { title: '订单详情' },
        hidden: true,
      },
      {
        path: 'deliverOrderList',
        name: 'deliverOrderList',
        component: () => import('@/views/oms/order/deliverOrderList.vue'),
        meta: { title: '发货列表' },
        hidden: true,
      },
      {
        path: 'orderSetting',
        name: 'orderSetting',
        component: () => import('@/views/oms/order/setting.vue'),
        meta: { title: '订单设置', icon: 'order-setting' },
      },
      {
        path: 'returnApply',
        name: 'returnApply',
        component: () => import('@/views/oms/apply/index.vue'),
        meta: { title: '退货申请处理', icon: 'order-return' },
      },
      {
        path: 'returnReason',
        name: 'returnReason',
        component: () => import('@/views/oms/apply/reason.vue'),
        meta: { title: '退货原因设置', icon: 'order-return-reason' },
      },
      {
        path: 'returnApplyDetail',
        name: 'returnApplyDetail',
        component: () => import('@/views/oms/apply/applyDetail.vue'),
        meta: { title: '退货申请详情' },
        hidden: true,
      },
    ],
  },
  {
    path: '/sms',
    component: Layout,
    redirect: '/sms/coupon',
    name: 'sms',
    meta: { title: '营销', icon: 'sms' },
    children: [
      {
        path: 'flash',
        name: 'flash',
        component: () => import('@/views/sms/flash/index.vue'),
        meta: { title: '秒杀活动列表', icon: 'sms-flash' },
      },
      {
        path: 'flashSession',
        name: 'flashSession',
        component: () => import('@/views/sms/flash/sessionList.vue'),
        meta: { title: '秒杀时间段列表' },
        hidden: true,
      },
      {
        path: 'selectSession',
        name: 'selectSession',
        component: () => import('@/views/sms/flash/selectSessionList.vue'),
        meta: { title: '秒杀时间段选择' },
        hidden: true,
      },
      {
        path: 'flashProductRelation',
        name: 'flashProductRelation',
        component: () => import('@/views/sms/flash/productRelationList.vue'),
        meta: { title: '秒杀商品列表' },
        hidden: true,
      },
      {
        path: 'coupon',
        name: 'coupon',
        component: () => import('@/views/sms/coupon/index.vue'),
        meta: { title: '优惠券列表', icon: 'sms-coupon' },
      },
      {
        path: 'addCoupon',
        name: 'addCoupon',
        component: () => import('@/views/sms/coupon/add.vue'),
        meta: { title: '添加优惠券' },
        hidden: true,
      },
      {
        path: 'updateCoupon',
        name: 'updateCoupon',
        component: () => import('@/views/sms/coupon/update.vue'),
        meta: { title: '修改优惠券' },
        hidden: true,
      },
      {
        path: 'couponHistory',
        name: 'couponHistory',
        component: () => import('@/views/sms/coupon/history.vue'),
        meta: { title: '优惠券领取详情' },
        hidden: true,
      },
      {
        path: 'brand',
        name: 'homeBrand',
        component: () => import('@/views/sms/brand/index.vue'),
        meta: { title: '品牌推荐', icon: 'product-brand' },
      },
      {
        path: 'new',
        name: 'homeNew',
        component: () => import('@/views/sms/new/index.vue'),
        meta: { title: '新品推荐', icon: 'sms-new' },
      },
      {
        path: 'hot',
        name: 'homeHot',
        component: () => import('@/views/sms/hot/index.vue'),
        meta: { title: '人气推荐', icon: 'sms-hot' },
      },
      {
        path: 'subject',
        name: 'homeSubject',
        component: () => import('@/views/sms/subject/index.vue'),
        meta: { title: '专题推荐', icon: 'sms-subject' },
      },
      {
        path: 'advertise',
        name: 'homeAdvertise',
        component: () => import('@/views/sms/advertise/index.vue'),
        meta: { title: '广告列表', icon: 'sms-ad' },
      },
      {
        path: 'addAdvertise',
        name: 'addHomeAdvertise',
        component: () => import('@/views/sms/advertise/add.vue'),
        meta: { title: '添加广告' },
        hidden: true,
      },
      {
        path: 'updateAdvertise',
        name: 'updateHomeAdvertise',
        component: () => import('@/views/sms/advertise/update.vue'),
        meta: { title: '编辑广告' },
        hidden: true,
      },
    ],
  },
  {
    path: '/p0',
    component: Layout,
    redirect: '/p0/members',
    name: 'p0',
    meta: { title: '会员运营', icon: 'ums' },
    children: [
      { path: 'site-profile', name: 'p0SiteProfile', component: () => import('@/views/p0/SiteProfile.vue'), meta: { title: '公司信息与隐私政策' } },
      { path: 'notices', name: 'p0Notices', component: () => import('@/views/p0/Operations.vue'), meta: { title: '公告管理' } },
      { path: 'consultations', name: 'p0Consultations', component: () => import('@/views/p0/Operations.vue'), meta: { title: '在线咨询' } },
      { path: 'members', name: 'p0Members', component: () => import('@/views/p0/P0Workbench.vue'), meta: { title: '会员管理' } },
      { path: 'partner', name: 'p0Partner', component: () => import('@/views/p0/P0Workbench.vue'), meta: { title: '合伙人管理' } },
      { path: 'cofounder', name: 'p0Cofounder', component: () => import('@/views/p0/P0Workbench.vue'), meta: { title: '联创管理' } },
      { path: 'points', name: 'p0Points', component: () => import('@/views/p0/P0Workbench.vue'), meta: { title: '双积分管理' } },
      { path: 'finance', name: 'p0Finance', component: () => import('@/views/p0/Finance.vue'), meta: { title: '财务中心' } },
      { path: 'dividend', name: 'p0Dividend', component: () => import('@/views/p0/P0Workbench.vue'), meta: { title: '每日贡献奖励池' } },
      { path: 'settings', name: 'p0Settings', component: () => import('@/views/p0/P0Workbench.vue'), meta: { title: '系统配置与日志' } },
      { path: 'system', name: 'p0SystemSettings', component: () => import('@/views/p0/SystemSettings.vue'), meta: { title: '平台设置' } },
      { path: 'images', name: 'p0Images', component: () => import('@/views/p0/ImageManager.vue'), meta: { title: '图片管理' } },
      { path: 'area-library', name: 'p0AreaLibrary', component: () => import('@/views/p0/AreaLibrary.vue'), meta: { title: '省市区街道地址库' } },
      { path: 'message-records', name: 'p0MessageRecord', component: () => import('@/views/p0/MessageRecords.vue'), meta: { title: '发送记录' } },
      { path: 'visit-statistics', name: 'p0VisitStatistics', component: () => import('@/views/p0/VisitStatistics.vue'), meta: { title: '访问统计' } },
      { path: 'database', name: 'p0Database', component: () => import('@/views/p0/Database.vue'), meta: { title: '数据库管理' } },
    ],
  },
  {
    path: '/ums',
    component: Layout,
    redirect: '/ums/admin',
    name: 'ums',
    meta: { title: '权限', icon: 'ums' },
    children: [
      {
        path: 'admin',
        name: 'admin',
        component: () => import('@/views/ums/admin/index.vue'),
        meta: { title: '用户列表', icon: 'ums-admin' },
      },
      {
        path: 'department',
        name: 'department',
        component: () => import('@/views/p0/Department.vue'),
        meta: { title: '部门管理', icon: 'ums' },
      },
      {
        path: 'login-log',
        name: 'loginLog',
        component: () => import('@/views/p0/LoginLog.vue'),
        meta: { title: '登录日志', icon: 'ums' },
      },
      {
        path: 'role',
        name: 'role',
        component: () => import('@/views/ums/role/index.vue'),
        meta: { title: '角色列表', icon: 'ums-role' },
      },
      {
        path: 'allocMenu',
        name: 'allocMenu',
        component: () => import('@/views/ums/role/allocMenu.vue'),
        meta: { title: '分配菜单' },
        hidden: true,
      },
      {
        path: 'allocResource',
        name: 'allocResource',
        component: () => import('@/views/ums/role/allocResource.vue'),
        meta: { title: '分配资源' },
        hidden: true,
      },
      {
        path: 'menu',
        name: 'menu',
        component: () => import('@/views/ums/menu/index.vue'),
        meta: { title: '菜单列表', icon: 'ums-menu' },
      },
      {
        path: 'addMenu',
        name: 'addMenu',
        component: () => import('@/views/ums/menu/add.vue'),
        meta: { title: '添加菜单' },
        hidden: true,
      },
      {
        path: 'updateMenu',
        name: 'updateMenu',
        component: () => import('@/views/ums/menu/update.vue'),
        meta: { title: '修改菜单' },
        hidden: true,
      },
      {
        path: 'resource',
        name: 'resource',
        component: () => import('@/views/ums/resource/index.vue'),
        meta: { title: '资源列表', icon: 'ums-resource' },
      },
      {
        path: 'resourceCategory',
        name: 'resourceCategory',
        component: () => import('@/views/ums/resource/categoryList.vue'),
        meta: { title: '资源分类' },
        hidden: true,
      },
    ],
  },
]

// createWebHistory（History 模式）地址格式（需要服务器配置）：http://domain.com/admin/home
// createWebHashHistory（Hash 模式）地址格式（会多带一个#号）：http://domain.com/admin/#/home
const router = createRouter({
  history: createWebHashHistory(),
  routes: constantRouterMap,
})

export default router

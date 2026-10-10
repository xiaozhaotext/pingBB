"""从 Phase 1 原始编号清单生成可复核的逐项源码差异矩阵。"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
source = (root / "Phase1-需求拆解与系统设计.md").read_text(encoding="utf-8")
items = re.findall(r"(?m)^- (REQ-[A-Z0-9-]+)\s+(.+)$", source)
assert len(items) == 134 and len({i for i, _ in items}) == 134

paths = {
    "home": "mall-app-web/src/pages/index/index.vue；mall/mall-portal/src/main/java/com/macro/mall/portal/controller/HomeController.java",
    "product": "mall-app-web/src/pages/product/list.vue；mall-app-web/src/pages/product/product.vue；mall/mall-portal/src/main/java/com/macro/mall/portal/controller/PmsPortalProductController.java",
    "cart": "mall-app-web/src/pages/cart/cart.vue；mall/mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsCartItemServiceImpl.java",
    "order": "mall-app-web/src/pages/order/order.vue；mall/mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPortalOrderServiceImpl.java",
    "user": "mall-app-web/src/pages/user/user.vue；mall/mall-portal/src/main/java/com/macro/mall/portal/controller/UmsMemberController.java",
    "goods_admin": "mall-admin-web/src/views/pms/product/index.vue；mall/mall-admin/src/main/java/com/macro/mall/controller/PmsProductController.java",
    "category_admin": "mall-admin-web/src/views/pms/productCate/index.vue；mall/mall-admin/src/main/java/com/macro/mall/controller/PmsProductCategoryController.java",
    "order_admin": "mall-admin-web/src/views/oms/order/index.vue；mall/mall-admin/src/main/java/com/macro/mall/controller/OmsOrderController.java",
    "rbac": "mall-admin-web/src/views/ums/role/index.vue；mall/mall-admin/src/main/java/com/macro/mall/controller/UmsRoleController.java",
    "admin_user": "mall-admin-web/src/views/ums/admin/index.vue；mall/mall-admin/src/main/java/com/macro/mall/controller/UmsAdminController.java",
    "dash": "mall-admin-web/src/views/home/index.vue（含静态 defaultLineChartData）",
    "sql": "mall/document/sql/mall.sql（已检索 76 个 CREATE TABLE，无商家/结算/双积分表）",
}


def classify(req: str, description: str):
    seg = req.split("-")
    area = seg[2]
    number = int(seg[3])
    if seg[1] == "FE":
        if area == "HOME":
            if number == 1:
                return "已支持", "复用", paths["home"] + "；mall-app-web/src/pages/product/search.vue", "通用商品搜索代码可复用。"
            if number == 2:
                return "已支持", "复用", paths["home"] + "；mall/document/sql/mall.sql（sms_home_advertise）", "Banner 数据和页面存在。"
            if number == 3:
                return "部分支持", "改造", "mall-app-web/src/pages/notice/notice.vue", "消息页为静态示例，无消息数据接口/已读状态。"
            if number in (4, 5):
                return "不支持", "新增/待确认", paths["home"] + "；" + paths["sql"], "首页有通用推荐，无自选/复购专区和专属积分账本；规则冻结。"
            return "部分支持", "改造", "mall-app-web/src/pages.json", "已有底部导航；原图五入口与现有路由需逐项调整。"
        if area == "PICK":
            special = "在线咨询" in description or "分享" in description
            if special:
                return "部分支持", "改造", paths["product"], "通用商品详情存在，原图指定交互未见完整闭环。"
            return "部分支持", "改造", paths["product"] + "；" + paths["cart"], "商品/加购通用代码存在；自选区类型和积分抵扣不存在。"
        if area == "REBUY":
            return "不支持", "新增/待确认", paths["product"] + "；" + paths["sql"], "无复购专区、复购积分及 30/70 专属确认流程；规则冻结。"
        if area == "CART":
            if number == 2:
                return "不支持", "改造", paths["cart"] + "；" + paths["sql"], "购物车无 merchant_id 和商家分组。"
            if number == 9:
                return "部分支持", "改造", paths["cart"] + "；" + paths["order"], "单商户结算存在；无按商家拆子单。"
            return "已支持", "复用", paths["cart"] + "；mall-app-web/src/apis/cart.ts", "通用购物车页面/API 存在，跨商家能力另行改造。"
        if area == "DIST":
            return "不支持/规则待确认", "新增/待确认", "mall/document/sql/mall.sql；mall-app-web/src/pages/user/user.vue", "未见合伙人/联创/邀请关系/佣金/双积分独立账本；原图金额与计酬规则不启用。"
        if area == "ME":
            if number in (1, 2, 3):
                return "部分支持", "改造", paths["user"], "基础会员资料存在；逐字段与原图核对。"
            if number == 12:
                return "已支持", "复用", paths["order"], "普通会员订单列表存在。"
            if number == 14:
                return "已支持", "复用", "mall-app-web/src/pages/user/productCollection.vue；mall/mall-portal/src/main/java/com/macro/mall/portal/controller/MemberProductCollectionController.java", "收藏页面/API 存在。"
            if number == 15:
                return "已支持", "复用", "mall-app-web/src/pages/user/readHistory.vue；mall/mall-portal/src/main/java/com/macro/mall/portal/controller/MemberReadHistoryController.java", "浏览历史页面/API 存在。"
            if number == 16:
                return "已支持", "复用", "mall-app-web/src/pages/address/address.vue；mall/mall-portal/src/main/java/com/macro/mall/portal/controller/UmsMemberReceiveAddressController.java", "收货地址页面/API 存在。"
            if number in (8, 9, 10, 11):
                return "不支持/规则待确认", "新增/待确认", paths["user"] + "；" + paths["sql"], "源码只有通用 integration 字段，无产品/复购双积分余额与流水。"
            return "不支持/规则待确认", "新增/待确认", paths["user"] + "；" + paths["sql"], "原图钱包、提现、推广收益、角色或咨询能力无对应闭环。"
    if area == "GOODS":
        base = paths["category_admin"] if number <= 4 else paths["goods_admin"]
        return "已支持" if number <= 4 or number in (5, 6, 7, 10) else "部分支持", "复用" if number <= 4 or number in (5, 6, 7, 10) else "改造", base, "通用商品/分类 CRUD、上下架存在；特殊字段需与原图逐项校验。"
    if area == "ORDER":
        return "部分支持", "改造", paths["order_admin"] + "；mall/document/sql/mall.sql（oms_order）", "单商户订单页面/API 存在；缺商家子单和商家范围。"
    if area == "MEMBER":
        if number in (4, 5, 9, 10):
            return "不支持/规则待确认", "新增/待确认", "mall/mall-admin/src/main/java/com/macro/mall/controller/UmsMemberLevelController.java；" + paths["sql"], "普通会员等级表不等于合伙人/联创、自选/复购订单分类。"
        return "不支持", "新增", "mall/mall-admin/src/main/java/com/macro/mall/controller/UmsMemberLevelController.java；mall-admin-web/src/views/ums（无会员列表页）", "仅见会员等级查询；无后台会员列表/详情/统计 API 与页面。"
    if area == "FIN":
        return "不支持/规则待确认", "新增/待确认", paths["dash"] + "；" + paths["sql"], "无真实资金/结算/佣金账本和对账；首页金额图为静态示例。"
    if area == "SERVICE":
        return "不支持", "新增", "mall-admin-web/src/views；mall/mall-admin/src/main/java/com/macro/mall/controller", "未见对应服务/隐私政策后台维护闭环。"
    if area == "DASH":
        if number in (2, 3):
            return "部分支持", "改造", paths["dash"], "页面存在但图表使用静态 defaultLineChartData，未连真实统计 API。"
        if number == 4:
            return "已支持", "复用", paths["dash"], "后台首页页面存在。"
        if number == 7:
            return "已支持", "复用", "mall-admin-web/src/views/layout/components/Navbar.vue；mall-admin-web/src/stores/user.ts", "退出入口与前端会话清理存在；服务端撤销另验。"
        return "不支持", "新增", "mall-admin-web/src/views/home/index.vue；mall-admin-web/src/views/layout/components/Navbar.vue", "未见该原图动作的完整闭环。"
    if area == "SET":
        if number == 5:
            return "已支持", "复用", "mall-admin-web/src/views/ums/menu/index.vue；mall/mall-admin/src/main/java/com/macro/mall/controller/UmsMenuController.java", "后台菜单管理存在。"
        if number == 14:
            return "部分支持", "改造", paths["rbac"], "角色/权限存在，部门组织结构未见。"
        if number in (15, 16):
            return "已支持", "复用", paths["admin_user"], "管理员 CRUD 与角色绑定存在；高权审计仍需补。"
        if number == 4:
            return "部分支持", "改造", "mall/mall-admin/src/main/java/com/macro/mall/controller/OssController.java；mall/mall-admin/src/main/java/com/macro/mall/controller/MinioController.java", "对象存储上传存在，原图上传参数配置未见完整管理。"
        if number == 17:
            return "部分支持", "改造", "mall/document/sql/mall.sql（ums_admin_login_log）；mall/mall-admin/src/main/java/com/macro/mall/service/impl/UmsAdminServiceImpl.java", "有日志表/写入，未见后台查询页面和 API。"
        return "不支持", "新增/待确认" if number in (18, 19, 20, 21) else "新增", "mall-admin-web/src/views；mall/mall-admin/src/main/java/com/macro/mall/controller；" + paths["sql"], "未见对应独立管理闭环；高风险数据库维护/备份需安全评审。"
    raise ValueError(req)


header = """# mall 与原图 124 条需求差异矩阵

> 文件名沿用本轮指定名称。**实际矩阵为 134 条**：Phase 1 初稿和最终验收报告各有 134 个唯一 `REQ` 编号，后者“约 124 条”是统计文字错误。原图为缩放附件，完整节点数仍需原始高清源图复核。此处保留全部已编号需求，不删除重复文字所对应的不同节点。

依据：`../../Phase1-需求拆解与系统设计.md` 中逐条原文、`../../Phase1-需求拆解与最终验收报告.md` 的追踪矩阵，以及本工作区三个官方仓库固定 SHA 的实际文件。状态是**静态源码审计结论**，不等于运行验收。`已支持`只表示该条通用功能有实际页面/API/数据结构；跨商家、安全和资金约束仍须专项验证。`待确认`规则不得启用。路径相对于 `mall-custom/`。

| 需求编号 | 原图识读需求（Phase 1 原文） | mall 原模块/文件证据 | 当前能力 | 处理方式 | 审计说明 |
|---|---|---|---|---|---|
"""
rows = []
for req, desc in items:
    status, action, evidence, note = classify(req, desc)
    cells = [req, desc, evidence, status, action, note]
    rows.append("| " + " | ".join(c.replace("|", "\\|").replace("\n", " ") for c in cells) + " |")
footer = """

## 专项约束（不冒充原图 REQ）

- 平台自营和第三方商家：`pms_product`、`oms_cart_item`、`oms_order`、`oms_order_item` 均无 `merchant_id`；缺商家主体、员工身份、主/子订单、商家结算。现有单商户商品/订单代码只作改造基础。
- 后端 RBAC：`mall-security` 的动态 URL 授权和 `ums_role`/`ums_resource` 可复用；商家行级数据隔离、跨商家管理审计未实现。
- 支付/库存：`OmsPortalOrderServiceImpl.paySuccess`、`lockStock` 有状态与并发约束缺口；不能将现有代码视为符合 Phase 1 的支付状态机、幂等与防超卖。
- 商家结算：没有应收、平台服务费、渠道手续费、佣金、退款调整、对账或异常补偿账务表和服务。

## 核对方法

1. 原需求逐条来源：初稿中 `^- REQ-` 行；脚本断言恰为 134 且编号唯一。
2. 源码检索：在 `mall/mall-admin/src`、`mall/mall-portal/src`、`mall/mall-mbg/src`、`mall/document/sql/mall.sql` 和两个前端的 `src` 中检索对应类、路由、表和 `merchant_id`/`seller_id`/分销等标识。
3. 对“未见实现”的判断限定于上述官方源码范围；不能推断任何外部私有插件。
4. 若取得原始 XMind/高清图，应复核 134 条节点原文、层级和遗漏，并追加新编号；不得把模糊内容猜成业务规则。
"""
target = Path(__file__).with_name("mall与原图124条需求差异矩阵.md")
target.write_text(header + "\n".join(rows) + footer, encoding="utf-8")
print(f"generated {target.name}: {len(rows)} rows")

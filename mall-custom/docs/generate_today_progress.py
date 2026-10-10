"""从已复核的 134 条编号生成当日逐条状态，避免遗漏编号。"""
from collections import Counter
from pathlib import Path

root = Path(__file__).parent
source = (root / '原图134条功能缺口复核.md').read_text(encoding='utf-8')
rows = []
for line in source.splitlines():
    if not line.startswith('| REQ-'):
        continue
    cells = [cell.strip() for cell in line.strip().strip('|').split('|')]
    req_id, title, audit = cells[:3]
    if '规则待确认' in audit:
        state = 'BLOCKED'
        evidence = '原图规则及合规口径未确认；未启用'
    elif audit in ('已支持', '部分支持'):
        state = 'PARTIAL'
        evidence = '官方通用能力存在；未完成该项页面/API/数据库全链路验收'
    else:
        state = 'NOT_STARTED'
        evidence = '无对应可验收闭环'
    overrides = {
        'REQ-FE-HOME-004': ('PARTIAL', '自选区页面/API/测试库商品归属已连通；积分购买未启用'),
        'REQ-FE-HOME-005': ('PARTIAL', '复购区页面/API/测试库商品归属已连通；30%/70% 结算未启用'),
        'REQ-FE-HOME-006': ('PASS', '浏览器核验四入口：首页/自选区/复购区/我的；购物车单独入口'),
        'REQ-FE-REBUY-001': ('PARTIAL', '专区列表与商品详情已在浏览器核验；推广与积分能力未实现'),
        'REQ-FE-REBUY-002': ('BLOCKED', '30% 积分/70% 现金计算、资格与退款口径未确认'),
        'REQ-FE-CART-001': ('PARTIAL', '真实会员 API 加入/读取购物车通过；全页操作未验收'),
        'REQ-FE-CART-005': ('PARTIAL', '篡改请求价 0.01 后服务器按数据库价 649 写入；页面未验收'),
        'REQ-FE-CART-009': ('PARTIAL', '本地 API 创建订单 99/100，幂等重放、模拟支付、取消释放库存通过；电脑/手机结算页面及多商家拆单未完整验收'),
        'REQ-FE-PICK-012': ('PARTIAL', '电脑端商品详情复制真实商品 URL 已在浏览器核验；原图其他分享形态未完成'),
        'REQ-FE-ME-016': ('PARTIAL', '收货地址添加/读取 API 在测试库通过；页面未验收'),
        'REQ-ADM-MEMBER-013': ('PARTIAL', '首页真实会员总数和今日订单数 API 已接通；注册通知未实现'),
    }
    if req_id in overrides:
        state, evidence = overrides[req_id]
    rows.append((req_id, title, state, evidence))

assert len(rows) == 134, f'需求行数异常: {len(rows)}'
assert len({row[0] for row in rows}) == 134, '需求编号不唯一'
counts = Counter(row[2] for row in rows)
out = [
    '# 今日 134 条需求开发进度',
    '',
    '日期：2026-10-10。依据：`原图134条功能缺口复核.md`。状态依据本地真实测试；官方源码存在不等于 PASS。原图模糊字与规则继续待确认。',
    '',
    f"当前状态：PASS {counts['PASS']}、PARTIAL {counts['PARTIAL']}、BLOCKED {counts['BLOCKED']}、FAIL {counts['FAIL']}、NOT_STARTED {counts['NOT_STARTED']}；合计 {len(rows)}。",
    '',
    '| 需求编号 | 原图功能 | 状态 | 本轮证据/原因 |',
    '|---|---|---|---|',
]
for req_id, title, state, evidence in rows:
    out.append(f'| {req_id} | {title.replace("|", "∣")} | {state} | {evidence} |')
out += [
    '',
    '## 判定说明',
    '',
    '- PASS 仅用于页面/API/数据及对应操作已真实核验的完整条目。',
    '- PARTIAL 包含官方通用能力但尚未完成联合验收；不代表按原图全部交付。',
    '- BLOCKED 涉及原图模糊规则、合规或资金口径，未开启真实交易。',
    '- RabbitMQ 已在本地便携环境连通；单商家测试订单 API 闭环通过，但多商家拆单和完整前端结算尚未验收。',
    '- NOT_STARTED 仍缺完整实现。',
]
(root / '今日134条需求开发进度.md').write_text('\n'.join(out) + '\n', encoding='utf-8')
print(dict(counts))

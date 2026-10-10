#!/usr/bin/env bash
# ============================================================================
# 电商 V1.0 —— P0 后端接口冒烟测试（闭环）
# 验证：会员身份 / 邀请 / 双积分(幂等+防负余额) / 合伙人模拟升级 /
#       收益与提现(模拟) / 每日分红池(多劳多得)
# 说明：所有资金类接口均为"模拟"，本脚本仅验证系统行为与一致性，不产生真实资金。
# 前置：后端 mall-portal 已启动（如 http://localhost:8085），且已有两个测试会员的 token。
# 用法： BASE=http://localhost:8085 TOKEN_A=xxx TOKEN_B=yyy bash p0-api-smoke-test.sh
# ============================================================================
set -u
BASE="${BASE:-http://localhost:8085}"
TA="${TOKEN_A:?请设置 TOKEN_A（会员A的登录token）}"
TB="${TOKEN_B:?请设置 TOKEN_B（会员B的登录token）}"
JQ_OK=$(command -v jq >/dev/null 2>&1 && echo 1 || echo 0)

call() { # method path token [json-body]
  local m="$1" p="$2" tk="$3" body="${4:-}"
  if [ -n "$body" ]; then
    curl -s -X "$m" "$BASE$p" -H "Authorization: $tk" -H "Content-Type: application/json" -d "$body"
  else
    curl -s -X "$m" "$BASE$p" -H "Authorization: $tk"
  fi
}
code() { # 从返回 JSON 取 code 字段
  if [ "$JQ_OK" = "1" ]; then echo "$1" | jq -r '.code'; else echo "$1" | grep -o '"code":[0-9]*' | head -1 | grep -o '[0-9]*'; fi
}
ok() { local c; c=$(code "$2"); [ "$c" = "200" ] && echo "✅ $1" || echo "❌ $1 (code=$c) body=$2"; }

echo "===== 1) 会员身份 ====="
R=$(call GET "/member/identity/current" "$TA"); ok "A 查询当前身份" "$R"

echo "===== 2) 邀请码（自动生成 + 幂等） ====="
R1=$(call GET "/member/invitation/myCode" "$TA"); ok "A 获取邀请码" "$R1"
CODE=$([ "$JQ_OK" = "1" ] && echo "$R1" | jq -r '.data' || echo "$R1" | grep -o '"data":"[^"]*"' | head -1 | sed 's/.*:"//;s/"//')
echo "    A 邀请码 = $CODE"
R2=$(call GET "/member/invitation/myCode" "$TA"); ok "A 再次获取(幂等同码)" "$R2"

echo "===== 3) B 绑定 A 为推荐人（防自改/防重复/防循环） ====="
ok "B 通过A邀请码绑定" "$(call POST "/member/invitation/bind" "$TB" "{\"code\":\"$CODE\"}")"
ok "B 重复绑定(应失败)" "$(call POST "/member/invitation/bind" "$TB" "{\"code\":\"$CODE\"}")"

echo "===== 4) 双积分钱包（幂等 + 防负余额） ====="
ok "A 积分变动 +100(商品积分)" "$(call POST "/member/points/change" "$TA" "{\"pointsType\":1,\"changeType\":\"earn\",\"amount\":100,\"bizId\":\"smoke1\",\"idempotencyKey\":\"SMOKE_A_1\",\"remark\":\"test\"}")"
ok "A 同 idempotencyKey 重复发放(应失败)" "$(call POST "/member/points/change" "$TA" "{\"pointsType\":1,\"changeType\":\"earn\",\"amount\":100,\"bizId\":\"smoke1\",\"idempotencyKey\":\"SMOKE_A_1\",\"remark\":\"test\"}")"
ok "A 扣减 9999 触发负余额(应失败)" "$(call POST "/member/points/change" "$TA" "{\"pointsType\":1,\"changeType\":\"consume\",\"amount\":-9999,\"bizId\":\"smoke2\",\"idempotencyKey\":\"SMOKE_A_2\",\"remark\":\"test\"}")"
ok "A 查询商品积分账户" "$(call GET "/member/points/account?pointsType=1" "$TA")"

echo "===== 5) 合伙人模拟购买 -> 身份升级 ====="
BID="P_SMK_$(date +%s)"
ok "A 创建合伙人模拟订单" "$(call POST "/member/partner/order" "$TA" "{\"bizId\":\"$BID\"}")"
ok "A 模拟支付并升级合伙人" "$(call POST "/member/partner/pay" "$TA" "{\"bizId\":\"$BID\"}")"
ok "A 再次支付(幂等,应失败)" "$(call POST "/member/partner/pay" "$TA" "{\"bizId\":\"$BID\"}")"
R=$(call GET "/member/identity/current" "$TA"); ok "A 身份应变为合伙人" "$R"

echo "===== 6) 收益与提现（模拟） ====="
ok "A 模拟发放收益 50" "$(call POST "/member/earnings/simulateReward" "$TA" "{\"amount\":50,\"bizId\":\"ER1\",\"idempotencyKey\":\"ER_A_1\",\"remark\":\"test\"}")"
ok "A 申请提现 20(手续费0.6%)" "$(call POST "/member/withdraw/apply" "$TA" "{\"amount\":20,\"idempotencyKey\":\"WD_A_1\"}")"
ok "A 提现列表" "$(call GET "/member/withdraw/list" "$TA")"

echo "===== 7) 每日分红池（多劳多得，比例分配） ====="
SD=$(date -u +%Y-%m-%d)
ok "A 记录完成订单贡献 +10" "$(call POST "/member/dividend/contribution" "$TA" "{\"eventType\":\"order_complete\",\"bizId\":\"D1\",\"contributionValue\":10,\"statDate\":$(date -u +%s000)}")"
ok "B 记录完成订单贡献 +30" "$(call POST "/member/dividend/contribution" "$TB" "{\"eventType\":\"order_complete\",\"bizId\":\"D2\",\"contributionValue\":30,\"statDate\":$(date -u +%s000)}")"
ok "模拟每日分配(pool=100)" "$(call POST "/member/dividend/allocate" "$TA" "{\"statDate\":$(date -u +%s000),\"poolTotal\":100,\"calcBatch\":\"SMK_BATCH_1\"}")"
ok "查询模拟分配快照" "$(call GET "/member/dividend/snapshot?statDate=$(date -u +%s000)" "$TA")"

echo "===== 完成。以上仅验证系统行为与一致性，所有资金均为模拟，无真实打款。 ====="

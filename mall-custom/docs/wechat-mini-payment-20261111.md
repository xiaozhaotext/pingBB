# 微信小程序订单支付

小程序 AppID 为 `wx734dae9c5f5285b6`，商户号为 `1752296458`。小程序和正式网页使用同一个后端，分别使用小程序与公众号的 AppID 和 openid。

## 服务端配置

新增环境变量 `MALL_WECHAT_MINI_ENABLED`、`MALL_WECHAT_MINI_APP_ID`、`MALL_WECHAT_MINI_APP_SECRET`、`MALL_WECHAT_MINI_NOTIFY_URL`。实际 AppSecret 仅保存在服务器 root 所有、权限 0600 的 `/etc/mall-demo/wechat-mini.env`，由 systemd 的 `EnvironmentFile` 注入。密钥值不得提交到 Git、前端 `.env`、日志或小程序包。

小程序回调地址为 `https://zhiyoumei.com.cn/portal/wechatPay/mini/notify`。商户私钥、APIv3 密钥、微信支付公钥和公钥 ID 沿用现有后端配置，验签模式为 PUBLIC_KEY。

先备份数据库，执行 `mall/document/sql/custom-v10/55_wechat_mini_payment.sql`，再部署同一提交构建的 portal.jar 和 H5。SQL 新增小程序身份及支付关联表，并兼容移除早期测试版的 openid 唯一索引。账号切换沿用既有会员登录方式，支付授权票据按会员隔离。回滚代码时保留支付关联表，避免丢失已发起支付的订单追踪信息。

## 正常流程

1. 用户使用现有会员账号登录。付款时小程序执行 `uni.login`，将临时 code 发往需要会员身份认证的 `POST /member/wechat-mini/login`。
2. 后端使用小程序 AppID 和服务器 AppSecret 请求 code2session，将 openid 关联当前会员。返回五分钟有效且绑定会员与当前微信身份的支付授权票据。session_key 和 openid 均留在后端。
3. `POST /wechatPay/mini` 接收商城订单号和支付票据，重新校验订单归属、状态和服务端金额，调用微信支付 APIv3 的 JSAPI 下单接口。此接口对应 JSAPI 交易类型。
4. 小程序以服务端签名参数调用 `uni.requestPayment`，在 MP-WEIXIN 平台编译为原生 `wx.requestPayment`。
5. 小程序通过 `POST /wechatPay/mini/status` 查询真实支付结果。原生调用返回成功后仍等待服务器确认。
6. 回调经 SDK 公钥验签、AppID/商户号/币种/金额校验后，将小程序微信交易号关联回原商城订单，并调用既有幂等支付确认流程。处理成功返回 204，未处理成功返回非 2xx 供微信重试。

小程序交易号为固定 32 字符的 MP 前缀编号，关联原商城订单，兼容原先从网页创建预支付的订单。数据库中的订单应付金额是唯一下单金额来源。

## 构建

前端 `.env.production` 包含正式 API 地址及公开构建开关，不包含 AppSecret。执行 `npm run build:mp-weixin` 构建小程序，执行 `npm run build:h5` 构建正式网页。小程序 AppID 固定为上述 AppID。打包时检查实际 AppSecret 字节未出现在任何客户端文件。

## 验证

后端包含 code2session 响应处理、身份绑定冲突、设备授权隔离、订单关联、精确金额、AppID/商户号、失败回滚、重复支付通知和前端支付取消/待确认状态的回归测试。实际收银台验证在测试会员中进行，停止于收银台展示。


## 2026-11-11 实际验证结果

- 测试会员 wufen（ID 42）成功完成小程序 code2session 授权及现有会员关联。
- 按商品真实售价生成 12.90 元订单 181，后端成功生成微信小程序 JSAPI 预支付参数。用户确认支付页正常显示并弹出微信收银台。
- 服务器通过微信公钥模式验证签名查单响应，结果为待付款。测试订单随后通过正常取消接口关闭。
- 本次实测覆盖收银台拉起，实付成功通知和到账未进行资金实付验收；验签、订单关联、金额及幂等处理由回归测试覆盖。
- 本地增量更新时开发者工具曾未注册新增支付工具模块，导致支付页面空白。原生支付帮助函数现已合并到已有的 wechatPay 工具模块，并检查完整客户端模块依赖。最终包使用全量构建输出。

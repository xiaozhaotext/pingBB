# 正式收款接入：旅游路线与会员

本次支付入口面向自选区旅游路线订单和独立会员购买，支持微信外部浏览器 H5、微信内置浏览器 JSAPI、支付宝手机网站支付。普通订单支付接口仅接受已有 `tri_order_snapshot` 的旅游路线订单；余额充值继续保持关闭。所有密钥、证书、商户号写入服务器专用环境文件；不要提交到 Git。

## 入口与回调

| 入口 | 微信异步通知 | 支付宝异步通知 |
| --- | --- | --- |
| 3999 旅游路线订单 | `https://<正式域名>/portal/wechatPay/notify` | `https://<正式域名>/portal/alipay/notify` |
| 独立会员购买 | `https://<正式域名>/portal/member/membership/notify/wechat` | `https://<正式域名>/portal/member/membership/notify/alipay` |

两条订单链路的商户配置使用不同前缀，可指向同一套已签约商户资料。微信需要 `MALL_WECHAT_*` 和 `MEMBERSHIP_WECHAT_*`；支付宝需要 `MALL_ALIPAY_*` 和 `MEMBERSHIP_ALIPAY_*`。字段清单见 `mall/mall-portal/src/main/resources/application.yml`。普通支付宝 `MALL_ALIPAY_APP_PRIVATE_KEY` 与 `MALL_ALIPAY_PUBLIC_KEY` 是密钥内容；会员支付宝 `MEMBERSHIP_ALIPAY_PRIVATE_KEY_PATH` 与 `MEMBERSHIP_ALIPAY_PUBLIC_KEY_PATH` 是服务器文件路径，后者须为支付宝公钥。微信两个私钥字段也均为服务器文件路径。正式支付宝网关统一使用 `https://openapi.alipay.com/gateway.do`。

## 发布顺序

1. 确认 `zhiyoumei.com.cn` HTTPS 证书有效、微信 H5 支付域名已审核通过、支付宝手机网站支付已签约上线。微信内 JSAPI 还需公众号 AppID 绑定商户号、开通 JSAPI、配置网页授权域名和支付授权目录。
2. 在服务器放置商户私钥与公钥文件，限制权限给服务进程读取；把环境变量写进只允许 root/服务组读取的文件。为两个入口分别配置上述公网通知 URL，不包含 `/sandbox/`。
   微信内支付还需设置 `MALL_WECHAT_OAUTH_APP_SECRET`、`MALL_WECHAT_OAUTH_CALLBACK_URL=https://zhiyoumei.com.cn/portal/member/wechat-oauth/callback`、`MALL_WECHAT_OAUTH_SITE_URL=https://zhiyoumei.com.cn`。两个微信支付入口如共用本 OAuth 配置，其 AppID 必须是同一个公众号 AppID。AppSecret 仅保存在服务端；OpenID 由服务端授权获取，客户端传入值不会用于支付。
3. 核对正式库 `mall_demo` 与沙箱库 `mall_demo_sandbox` 分离，先备份正式库及现有 JAR、静态文件、Nginx/systemd 配置。生产 `mall.payment.mock.enabled=false`，沙箱配置保持独立。
4. 发布最新 `main` 的 portal JAR 和正式 H5 构建。普通订单前端构建需 `VITE_USE_WECHAT_PAY=true`、`VITE_USE_ALIPAY=true`、`VITE_USE_MOCK_PAYMENT=false`、`VITE_SANDBOX_MODE=false`、`VITE_API_BASE_URL=/portal`。独立会员按钮根据服务端 `/member/membership/payment-options` 的渠道就绪状态显示。
5. 核对 systemd 生效配置，移除会覆盖环境变量的 `--mall.membership.payment.enabled=false` 启动参数。只开放本次两条链路：`mall.payment.wechat.enabled=true`、`mall.payment.alipay.enabled=true`、`mall.membership.payment.enabled=true` 以及会员微信/支付宝子开关；继续保持模拟支付、余额充值、分红自动发放关闭。正式库 `membership.purchase.enabled` 在回调路径和凭据核对完成后改为 `1`。
6. Nginx 允许四个通知地址及普通订单支付宝发起路径通过 `/portal/` 代理；删除旧的 `/portal/alipay/` 拦截和可能存在的 `/portal/member/membership/` 拦截。保留 `/portal/mockPayment/` 拦截。执行 `nginx -t` 并重载。
7. 先各下一笔受控真实测试单：旅游路线和独立会员分别用微信内 JSAPI、微信外 H5、支付宝支付；逐笔核对渠道交易号、订单状态、身份、3999+3999 积分、三池 500/200/200 明细及重复回调幂等。出现任何不符即关闭相应支付开关，保留订单和渠道账单用于核对。

回调成功是入账依据，浏览器回跳不是。不要把密钥、公钥内容、APIv3 密钥或生产数据库口令写入构建日志或 GitHub。

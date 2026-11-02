# 沙箱站微信内 JSAPI 联调

当前 `main` 已有服务端公众号网页授权（`/member/wechat-oauth/authorize`、`/callback`、`/status`）。服务端将 OpenID 与当前登录会员绑定，不接受浏览器传入的 OpenID；普通订单和身份牌共用这份授权结果。直调 JSAPI 且没有授权时返回 HTTP 400。前端微信内走 JSAPI，微信外走原 H5 接口。

## 沙箱配置

以下配置只放在独立的沙箱服务进程和沙箱构建中，正式站保持 `mall.payment.wechat.enabled=false`、`mall.membership.payment.enabled=false`、`mall.payment.mock.enabled=false`。商户号、AppID、结算资料不需要改动。

1. 沙箱 portal 必须连接独立的 `mall_demo_sandbox` 库。先核对服务实例的数据库地址、数据库名、Nginx `/sandbox/portal/` 代理及真实支付回调地址。`MALL_WECHAT_NOTIFY_URL` 使用 `https://zhiyoumei.com.cn/sandbox/portal/wechatPay/notify`；`MEMBERSHIP_WECHAT_NOTIFY_URL` 使用 `https://zhiyoumei.com.cn/sandbox/portal/member/membership/notify/wechat`。
2. 沙箱服务端环境配置公众号 AppSecret、商户 API 私钥文件路径、APIv3 密钥、商户证书序列号、通知地址，以及 `MALL_WECHAT_OAUTH_CALLBACK_URL=https://zhiyoumei.com.cn/sandbox/portal/member/wechat-oauth/callback` 和 `MALL_WECHAT_OAUTH_SITE_URL=https://zhiyoumei.com.cn/sandbox`。不得在日志或仓库记录密钥值。Nginx 必须透传 `Cookie`、`Set-Cookie`，且开放 OAuth 回调及两条支付通知路径。
3. 沙箱服务端关闭模拟支付 `mall.payment.mock.enabled=false`，开启普通订单微信支付和独立会员微信支付开关。正式站实例的同名开关保持关闭。沙箱如要测试普通商品 1 元现金订单，单独设 `MALL_WECHAT_SANDBOX_ONE_YUAN_TEST_ENABLED=true`；代码还会要求当前数据库名精确为 `mall_demo_sandbox`、订单总额和现金实付均精确为 1 元、余额抵扣为 0。此例外不会开通会员权益，也不能用于正式库。
4. 沙箱 H5 构建设置 `VITE_API_BASE_URL=/sandbox/portal`、`VITE_SANDBOX_MODE=true`、`VITE_USE_WECHAT_PAY=true`、`VITE_USE_MOCK_PAYMENT=false`、`VITE_USE_ALIPAY=false`。仓库默认 `.env.sandbox` 仍保留模拟支付开关，联调构建通过部署环境变量覆盖；不要把真实支付默认打开。

## 验收

在沙箱建立一笔非自选区、无余额抵扣的 1 元普通商品订单。微信内打开支付页，依次核对网页授权回跳、JSAPI 调起、支付通知验签、`oms_payment_confirmation` 渠道交易号及订单状态。未经授权直调 `/wechatPay/jsapi` 应返回 HTTP 400；微信外仍调用 `/wechatPay/h5`。身份牌另用 3999 元沙箱真实订单核对会员身份、积分和三池权益，不能用 1 元订单代替。退款可由商户平台按该笔真实渠道交易办理并核对结果；当前后台未实现微信自动退款，不得将人工退款显示为系统自动退款成功。

若微信支付公钥模式下出现平台证书下载失败或通知验签失败，停止联调并记录不含密钥的错误与 `Wechatpay-Serial` 类型，交由商户资料保管方核对公钥 ID 和公钥文件；不得用占位公钥或自行生成公钥切换验签配置。

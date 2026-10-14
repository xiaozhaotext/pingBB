# 会员购买测试沙箱

`https://122.51.105.107/sandbox/` 用于在商户资料未配置时验收购买链路。演示支付不会扣款；订单、积分、身份、直推奖励只写入 `mall_demo_sandbox`。正式库 `mall_demo` 的 `membership.purchase.enabled` 必须保持 `0`，分红开关在两库都保持 `0`。

部署使用独立的 `mall-portal-sandbox.service`（127.0.0.1:8086）、Redis DB 1、RabbitMQ `/mall_sandbox` 和 Nginx 的 `mall-sandbox.nginx.inc`。沙箱服务复用 `/etc/mall-demo/portal.env` 的连接凭据，但通过启动参数指定独立数据库与消息命名空间。数据库用户仅需沙箱库的读写权限；商户号、密钥和证书不应写进仓库。

构建沙箱 H5：

```powershell
$env:VITE_API_BASE_URL='/sandbox/portal'
$env:VITE_SANDBOX_MODE='true'
npm run build:h5
Remove-Item Env:VITE_API_BASE_URL,Env:VITE_SANDBOX_MODE
```

将 `dist/build/h5/` 部署到 `/opt/mall-demo/sandbox/`。测试脚本在服务器上运行：

```sh
python3 /home/ubuntu/workspace/test-sandbox-membership.py
```

脚本创建两个随机测试会员，绑定直属推荐关系，演示购买合伙人并全价升级联创，随后核对订单、双积分流水、身份变更、直推现金和重复确认的幂等性。它拒绝连接非本机的 API。正式微信/支付宝收款、短信、提现打款和分红发放不由这个沙箱验证。

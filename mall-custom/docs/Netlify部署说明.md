# Netlify 消费者商城部署说明

## 2026-10-10 现场诊断

`https://wufenceshi.netlify.app/` 和 `/index.html` 均实际返回 HTTP 404。当前 Netlify 站点没有在发布根目录提供 H5 的 `index.html`。本地执行 `uni build -p h5` 成功，入口文件位于 `mall-app-web/dist/build/h5/index.html`。仅凭公开 404 无法确定 Netlify 后台是未部署、构建失败，还是发布目录设置错误。

## 发布设置

如果 Netlify 连接的 Git 仓库根目录是 `mall-app-web`：

- Base directory：留空。
- Build command：`npm run build:h5`。
- Publish directory：`dist/build/h5`。
- Node.js：使用与本项目依赖兼容的版本；安装依赖优先 `npm ci`，保留 `package-lock.json`。

本仓库的 `mall-app-web/netlify.toml` 已写入构建命令、发布目录和 SPA 回退规则。若 Netlify 连接的是 `mall-custom` 上层仓库，先把 Base directory 设为 `mall-app-web`，或按 Netlify 实际仓库结构调整。若采用手工拖拽部署，应上传 `dist/build/h5` **目录中的内容**，不能上传源码根目录。

本地修改不会自动出现在 Netlify；需要把更改提交并推送到 Netlify 连接的仓库，或手工重新上传构建产物。重新部署后先检查 `/` 与 `/index.html` 均返回 200。

## API 边界

Netlify 当前只托管 Vue/uni-app 静态文件，不能运行本项目的 Spring Boot、MySQL、Redis 或 RabbitMQ。原生产配置中的 `http://localhost:8085` 会指向访问者自己的电脑，已从 `.env.production` 移除。上线可操作功能之前，必须另行部署后端至经授权的 HTTPS 测试地址，并在 Netlify 环境变量中设置 `VITE_API_BASE_URL=https://你的后端域名`；后端同时要明确允许该 Netlify 域名跨域访问。`VITE_` 环境变量会编入前端公开产物，不能放密钥。

当前没有获得线上后端部署授权，也没有可用的公开 HTTPS API 地址。即使静态页面重部署成功，登录、商品、购物车和订单仍不能在公网正常工作；不能把页面可打开当作商城已上线。生产构建已关闭本地模拟支付按钮，真实支付继续关闭。

## 本地验证记录

- `uni build -p h5`：PASS；日志 `docs/storefront-netlify-build-2026-10-10.log`。
- `dist/build/h5/index.html`：存在，899 字节。
- 搜索构建后 JS 中 `localhost:8085`：无匹配。
- `curl.exe -L -o NUL -w '%{http_code}' https://wufenceshi.netlify.app/`：404。
- 同一命令检查 `/index.html`：404。

重新部署后应再次实际检查上述两个 URL，并在浏览器 Network 面板核对 API 请求地址、响应和 CORS；本报告没有把线上修复标为 PASS。

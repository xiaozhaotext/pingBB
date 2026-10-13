# 缺口修复真实测试

结果汇总：`actual-results-2026-10-13.json`，完整解释见 `docs/缺失功能补齐与HTTPS部署验收-2026-10-13.md`。

这些脚本使用真实 MySQL、Redis、后端 API 和浏览器，不创建假响应。交易测试只能连接隔离测试库及本地模拟支付服务，禁止运行于真实资金环境。

从 mall-custom 根目录运行，Python 标准库用于 API 测试；UI 测试需要 Playwright。浏览器脚本当前引用本机提供的 Playwright 安装路径，其他机器需改为自己的安装路径。

私有运行配置不在仓库中：`.tools/consultation-test-accounts.json` 为测试账号数组（username/password），`.tools/server-admin-password.txt` 为本机管理员凭据，`.tools/deploy-ssh/id_ed25519` 为已授权部署私钥。不得提交这些文件；不能将生成的测试令牌打印到公开日志。

隔离 API 默认18080/18085，SSH查询指定 `mall_github_audit_20261013`；需要先配置对应隔离数据库、消息队列与本地服务。公网 `https-api-final.py` 会生成明确标注测试账号、咨询和公告，需要用户已有的演示测试授权；不会进行支付或提现。

```powershell
python tests/gap_repair/gap-api-release.py
python tests/gap_repair/gap-trade-100.py
python tests/gap_repair/registration-api.py
python tests/gap_repair/https-api-final.py
node tests/gap_repair/server-https-ui-final.cjs
```

公网浏览器目前连接超时，脚本失败就必须记为失败或环境阻塞。不要删除断言或把API成功替代浏览器成功。审计数据与测试账号保留用于复核，清理前需单独确认。

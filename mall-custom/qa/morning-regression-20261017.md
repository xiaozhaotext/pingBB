# 2026-10-17 上午改动完整测试记录

测试基线：`110e116fb5806178b0d9a09040b808d85efef0c3`（已包含 PR #9、#10、#11）。这些分支已合入远端 main，本地已快进拉取，无需重复合并。

额外统一会员权益说明中的“3999消费积分”为“3999商品积分”，与积分账户、消费区的名称一致。未修改积分类型、数额或资金逻辑。

## 前端结果

- 应用端全部自动化测试：47 项通过，0 失败，0 跳过。
- 管理端全部自动化测试：8 项通过，0 失败，0 跳过。
- 新增 13 项专项测试，实际执行图片 URL 转换、上海时间跨天格式化、邀请码失败/重试/重复提交/绑定成功、手机号清洗，以及后台订单/售后标签异步响应竞争。
- 应用端 `npm run tsc` 通过；管理端两次构建均包含 `vue-tsc --build`，通过。
- 正式/沙盒 H5、正式/沙盒管理端四份构建通过。术语调整后正式 H5 再次构建通过，沙盒产物已确认包含新术语。

```powershell
# mall-app-web
node --test tests/*.test.cjs scripts/test-*.cjs
npm run tsc
npm run build:h5
npm run build:h5:sandbox
# mall-admin-web
node --test tests/*.test.cjs
npm run build
```

构建使用对应环境的 `VITE_SANDBOX_MODE`、`VITE_API_BASE_URL` 或 `VITE_BASE_SERVER_URL`。现有 Sass 弃用和包体积提示不影响构建结果。

## 后端结果：未全部通过

完整执行 common、admin、portal 三模块的测试：**219 项，209 项通过，3 项断言失败，7 项运行错误，0 跳过**。

| 模块 | 总数 | 断言失败 | 运行错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| common | 16 | 0 | 0 | 0 |
| admin | 71 | 0 | 0 | 0 |
| portal | 132 | 3 | 7 | 0 |

本次新增的手机号查询测试 `MemberPhoneLoginLookupTest` 两项均通过。不能把完整 Maven 命令标记为成功，最终结果是 `BUILD FAILURE`。

| 未通过的测试 | 数量 | 实际观察 |
| --- | ---: | --- |
| `MallPortalApplicationTests`、`PortalProductDaoTests`、`PortalOrderStockConcurrencyTests` | 6 个错误 | 完整 Spring 上下文仍读取开发配置的 `localhost:3306/mall`，连接失败；独立 JDBC 测试环境变量不覆盖 Spring 默认数据源。 |
| `MembershipVerifiedPaymentHandlerMysqlTest` | 1 个失败、1 个错误 | 预期旧 `member_point_ledger` 无记录，实际有 2 条；推荐奖励用例提示“会员购买规则尚未启用”。需分别核对双写约定与测试库规则配置。 |
| `ZonePreviewHttpTest` | 2 个失败 | HTTP 返回服务端错误；根因为结算代码读取 `CartPromotionItem.integration` 的 null 值并拆箱，触发 NPE。需核对适配器空值约定，不能以构建通过代替此项验收。 |

首次运行另有 4 项站点资料测试因缺少隔离 MySQL 配置失败；配置独立测试库后重跑，四项全部通过，最终统计取重跑结果。

```powershell
# 在 mall-custom/mall 下；独立 JDBC URL/用户/密码经进程环境变量提供，不入库
mvn -pl mall-admin,mall-portal -am package '-DskipTests=false' '-Ddocker.skip=true' '-Dspring.http.client.factory=simple' '-DargLine=-Djdk.net.unixdomain.tmpdir=D:/Project/Online_business'
```

测试 MySQL 使用单独的 `mall_github_audit_20261017_*` 库、专属账号和仅绑定 127.0.0.1 的 SSH 转发。只复制沙盒表结构和一条站点资料初始化记录。测试数据库、专属账号、凭证文件及转发已清理；测试未向正式或沙盒业务库写入交易。

本轮未部署服务器，也未完成浏览器逐页点击验收；图片测试确认 URL 归一化逻辑，并不等于已验证线上每张图片的 HTTP 内容。旧页面的 `/sandbox/oss/` 兼容规则仍需在实际发布时同步配置。

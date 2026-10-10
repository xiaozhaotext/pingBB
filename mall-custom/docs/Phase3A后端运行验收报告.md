# Phase 3A 后端运行验收报告

日期：2026-10-08。工作区：`C:\Users\SK\Desktop\平台测试\mall-custom`。**当前未取得官方 Java 后端运行基线**；本机缺少必要环境，安装授权已请求但尚未收到，未擅自安装系统服务或运行生产资金接口。

## 1. 源码与环境

- 后端 `mall` 固定提交 `dcaa93b3150352e5044708d7211b5bed0af4509f`，分支 `master`，`origin=https://github.com/macrozheng/mall.git`。本轮开始时用 `GIT_NO_LAZY_FETCH=1; git status --short` 得到空变更；该环境变量避免上一轮 GitHub promisor 缺失对象导致状态命令卡住。后端工作树原先用固定 SHA 官方归档恢复，717 个文件的 blob 哈希已在上一轮验证一致；历史 Git 对象仍需补齐。
- 系统 Windows 10 / PowerShell；Python `3.11.9`、Node `v24.19.0`。`java -version`、`mvn -version`、`mysql --version`、`redis-cli ping` 均提示命令未识别；本机 3306、6379 端口探测均为 `False`，服务列表未见 MySQL/Redis。未查到 `JAVA_HOME`/`MAVEN_HOME`。
- 官方 `mall/pom.xml` 需 Java 17、Spring Boot 3.5.14、Maven；`mall/mall-portal/pom.xml` 还依赖 MySQL/MyBatis、Redis、RabbitMQ、MongoDB、支付宝 SDK，`mall-search` 依赖 Elasticsearch。最小启动的外围服务范围要在安装授权后实际验证。

## 2. 实际验收记录

| 项目 | 实际命令 | 结果 | 日志/错误摘要 |
|---|---|---|---|
| JDK/Maven 可用性 | `java -version`、`mvn -version` | BLOCKED | 两命令均未识别；因此未执行 `mvn -pl mall-admin,mall-portal -am -DskipTests=false test/package` |
| MySQL/Redis | `mysql --version`、`redis-cli ping`、`Test-NetConnection localhost -Port 3306/6379` | BLOCKED | 客户端命令未识别，两个端口均不可连接；未创建测试库和测试账号 |
| 原版 SQL 初始化 | 只读取 `mall/document/sql/mall.sql` | BLOCKED | 无 MySQL 8；SQL 带 `DROP TABLE IF EXISTS` 与样例数据，未向任何现有库执行 |
| Mapper XML 静态解析 | `python -c "import xml.etree.ElementTree as E; E.parse('mall-portal/src/main/resources/dao/PortalOrderDao.xml'); print('mapper_xml_ok')"` | PASS（仅静态） | 输出 `mapper_xml_ok`；不能替代 MyBatis 启动或数据库语义检查 |
| 后端服务启动 | 未执行 | BLOCKED | 缺 Java、Maven、数据库和缓存依赖 |
| 登录 API 冒烟 | 未发 HTTP 请求 | BLOCKED | 后端未启动，无响应可报告 |
| 商品 API 冒烟 | 未发 HTTP 请求 | BLOCKED | 后端未启动 |
| 订单 API 冒烟 | 未发 HTTP 请求 | BLOCKED | 后端未启动；真实支付入口默认关闭 |

## 3. 环境安装范围与批准边界

已请求批准以下**本机开发用途**变更：JDK 17、Maven、MySQL 8、Redis 的安装或便携部署；独立测试服务、空测试数据库和最小权限测试账号。根据启动结果，再单独确认 RabbitMQ、MongoDB、Elasticsearch 是否为所验收 API 的硬依赖。安装系统服务、改 PATH/防火墙/代理或下载大型中间件之前均须取得明确授权。本报告不构成安装授权。

授权后顺序：验证版本和签名/来源 → 新建独立测试库 → 审核并导入官方 SQL → 运行 Maven 编译/测试 → 启动 `mall-admin`、`mall-portal` → 用测试账号执行登录、商品、订单只读/安全冒烟 → 保存命令、日志、HTTP 状态与请求 ID。需要确保本轮代码的 `mall.payment.alipay.enabled=false`，不可对外暴露测试后端或调用真实支付。

## 4. 当前结论

代码静态审计和 Mapper XML 语法检查不能证明后端可编译、可运行或事务安全。**Phase 3A 后端运行验收未通过**；在获得环境安装批准及完成真实测试前，不得宣布交易安全整改已验收。

## 5. 2026-10-08 本机授权后的复验（覆盖上文环境阻塞状态）

用户已授权本机安装。Temurin 17.0.20.1、Maven 3.9.16、MySQL 8.4.11 便携进程已可用；`java -version`、`mvn -version` 实测通过。官方 `mall.sql` 导入独立测试库成功，原版 76 表。`mvn -B -ntp -pl mall-portal,mall-admin -am '-DskipTests=true' '-Ddocker.skip=true' package` 编译打包 PASS；`-DskipTests=false ... test` 最终 12/12 PASS。admin/portal 分别在本机 8080/8085 启动，测试管理员登录与商品 API 返回业务码 200，订单越权拒绝且未改库。首次默认 `package` 因官方 POM 的远程 Docker 镜像构建而失败，首次 JUnit 因默认 3306 连接而失败，均已用进程级参数和专用测试库复验。Garnet 验证 Redis 协议兼容命令，但真实 Redis Server 未运行；RabbitMQ/MongoDB 仍未具备。完整命令、日志、范围和限制见《Phase3A最终运行验收报告.md》。

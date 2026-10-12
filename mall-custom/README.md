# 智美游：换电脑继续开发

仓库：https://github.com/xiaozhaotext/pingBB ，使用最新 main 分支。

```powershell
git clone git@github.com:xiaozhaotext/pingBB.git
cd pingBB/mall-custom
```

无 SSH 凭据时可用 HTTPS 克隆。修改完成后提交并推送，下次开发前先 git pull。商城源码在 mall-app-web，管理后台在 mall-admin-web，官方 Java 后端及二创代码在 mall。服务器部署目录不属于本地开发目录。

## Git 中包含什么

业务源码、迁移与 SQL 草案、前端锁文件、启动脚本、部署示例、验收报告和已提交测试代码。近期品牌、摄影 UI、导航修复和推荐商品相关代码已纳入历史提交。

Git 不包含本地安装的工具、node_modules、target/dist 产物、数据库数据和真实密码。换电脑时必须重新准备依赖和测试数据库；只下载源码不能还原服务器商品、订单、会员数据。不要将生产数据或私钥加入仓库。

## 环境与构建

准备 JDK 17、Maven、Node.js、MySQL 和 Redis。完整下单还依赖 RabbitMQ。版本约束及依赖以三个工程实际 pom.xml/package.json/锁文件为准。

```powershell
cd mall
mvn -pl mall-admin,mall-portal -am package -Ddocker.skip=true
cd ../mall-admin-web
npm ci
npm run dev -- --host 127.0.0.1 --port 5173
```

消费者前端在另一个终端运行：

```powershell
cd mall-app-web
npm ci
node node_modules/@dcloudio/vite-plugin-uni/bin/uni.js -p h5 --host 127.0.0.1 --port 5174
```

前端 API 地址配置以各工程环境示例、src 中 HTTP 配置及 deploy 目录为准；开发前核对本机实际后端地址，不使用服务器生产密钥。

## 当前 Windows 后端启动脚本的配置约定

start-local-backends.bat 启动 admin 8080 和 portal 8085；start-portal-8085.bat 只启动 portal。脚本路径使用自身目录 `%~dp0`，无需使用原作者桌面绝对路径。

当前脚本使用这些本地文件（不提交）：

- `.tools/jdk17/jdk-17.0.20.1+1/bin/java.exe`：如使用其他 JDK 安装目录，调整脚本 JAVA 路径。
- `.tools/mysql-app-client.ini`：包含 `password=你自己测试数据库的密码`。数据库目前约定为 127.0.0.1:3307/mall_phase3a，账号 mall_test；如果本机不同，调整脚本中的连接信息。
- `.tools/rabbitmq-test-password.txt`：仅一行本机测试 RabbitMQ 密码；用户名 mall_test，虚拟主机 /mall，端口 5672。
- `mall/mall-admin/target/mall-admin-1.0-SNAPSHOT.jar` 与 portal 的对应 JAR：先构建生成。

先启动 MySQL、Redis、RabbitMQ，并按已存在的数据库文档准备独立测试库，再运行脚本。脚本会检查上述文件，缺失时给出错误；不会自动下载工具、导入完整数据或安装系统服务。

这些说明根据目前代码和脚本整理，不代表另一台电脑已经执行过安装、迁移或运行验收。不要将现有商城宣称为全部 134 项业务均通过；功能与阻塞以 docs 中验收报告为准。

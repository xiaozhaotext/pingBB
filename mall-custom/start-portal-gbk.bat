@echo off
rem 单独启动 mall-portal (商城前台 API)，监听 127.0.0.1:8085
rem 用途：全套已由 一键启动商城.bat 拉起、仅需单独重启 portal 时使用
setlocal DisableDelayedExpansion
set "BASE=C:\Users\SK\Desktop\平台测试\mall-custom"
set "JAVA=%BASE%\.tools\jdk17\jdk-17.0.20.1+1\bin\java.exe"
set "JAR=%BASE%\mall\mall-portal\target\mall-portal-1.0-SNAPSHOT.jar"

if not exist "%JAVA%" (echo Missing local JDK 17.& pause & exit /b 1)
if not exist "%JAR%" (echo Missing portal JAR. Build it first.& pause & exit /b 1)

set "DBPASS="
for /f "usebackq tokens=1,* delims==" %%A in ("%BASE%\.tools\mysql-app-client.ini") do if /I "%%A"=="password" set "DBPASS=%%B"
if not defined DBPASS (echo Test DB password not found.& pause & exit /b 1)

set "RABBITPASS="
for /f "usebackq delims=" %%A in ("%BASE%\.tools\rabbitmq-test-password.txt") do set "RABBITPASS=%%A"
if not defined RABBITPASS (echo RabbitMQ test password not found.& pause & exit /b 1)

set "SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3307/mall_phase3a?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
set "SPRING_DATASOURCE_USERNAME=mall_test"
set "SPRING_DATASOURCE_PASSWORD=%DBPASS%"
set "SPRING_RABBITMQ_HOST=127.0.0.1"
set "SPRING_RABBITMQ_PORT=5672"
set "SPRING_RABBITMQ_USERNAME=mall_test"
set "SPRING_RABBITMQ_PASSWORD=%RABBITPASS%"
set "SPRING_RABBITMQ_VIRTUAL_HOST=/mall"
set "MALL_PAYMENT_MOCK_ENABLED=true"

cd /d "%BASE%\mall"
start "Mall Portal API 8085" "%JAVA%" -Xmx512m -jar "%JAR%" --server.address=127.0.0.1 --server.port=8085
echo Portal API starting in a new window on http://127.0.0.1:8085/
timeout /t 3 >nul
endlocal

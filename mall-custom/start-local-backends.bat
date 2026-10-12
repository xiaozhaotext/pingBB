@echo off
rem Start mall-admin (8080) and mall-portal (8085) locally, each in its own window.
rem Use this when the front-end dev servers are already running and only the
rem Java back ends need to come back up.
setlocal DisableDelayedExpansion
set "BASE=%~dp0"
set "JAVA=%BASE%.tools\jdk17\jdk-17.0.20.1+1\bin\java.exe"
set "ADMIN_JAR=%BASE%mall\mall-admin\target\mall-admin-1.0-SNAPSHOT.jar"
set "PORTAL_JAR=%BASE%mall\mall-portal\target\mall-portal-1.0-SNAPSHOT.jar"
set "DBINI=%BASE%.tools\mysql-app-client.ini"
set "RABBITTXT=%BASE%.tools\rabbitmq-test-password.txt"

if not exist "%JAVA%" (echo Missing local JDK 17.& pause & exit /b 1)
if not exist "%ADMIN_JAR%" (echo Missing admin JAR. Build it first.& pause & exit /b 1)
if not exist "%PORTAL_JAR%" (echo Missing portal JAR. Build it first.& pause & exit /b 1)
if not exist "%DBINI%" (echo Missing local MySQL credentials file. See README.md.& pause & exit /b 1)
if not exist "%RABBITTXT%" (echo Missing local RabbitMQ credentials file. See README.md.& pause & exit /b 1)

set "DBPASS="
for /f "usebackq tokens=1,* delims==" %%A in ("%DBINI%") do if /I "%%A"=="password" set "DBPASS=%%B"
if not defined DBPASS (echo Test DB password not found.& pause & exit /b 1)
set "RABBITPASS="
for /f "usebackq delims=" %%A in ("%RABBITTXT%") do set "RABBITPASS=%%A"
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

cd /d "%BASE%mall"
start "Mall Admin API 8080" "%JAVA%" -Xmx768m -jar "%ADMIN_JAR%" --server.address=127.0.0.1 --server.port=8080
start "Mall Portal API 8085" "%JAVA%" -Xmx768m -jar "%PORTAL_JAR%" --server.address=127.0.0.1 --server.port=8085

echo Admin API  : http://127.0.0.1:8080/
echo Portal API : http://127.0.0.1:8085/
echo Admin web  : http://127.0.0.1:5173/
echo Mobile web : http://127.0.0.1:5174/
echo.
echo Each service opened in its own window. Give them about 40 seconds to boot.
echo Close those windows to stop the services.
set "DBPASS="
set "RABBITPASS="
pause
endlocal

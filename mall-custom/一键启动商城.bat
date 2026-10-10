@echo off
setlocal DisableDelayedExpansion
set "ROOT=%~dp0"
set "JDK=%ROOT%.tools\jdk17\jdk-17.0.20.1+1"
set "MYSQL=%ROOT%.tools\mysql\mysql-8.4.11-winx64\bin\mysqld.exe"
set "GARNET=%ROOT%.tools\garnet\net8.0\GarnetServer.exe"
set "RABBIT=%ROOT%.tools\rabbitmq\rabbitmq_server-4.2.9\sbin\rabbitmq-server.bat"
set "ERLANG=%ROOT%.tools\erlang\otp-27.3.4.14"
set "NODE_BUNDLED=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin"

if /I "%~1"=="mysql" goto run_mysql
if /I "%~1"=="cache" goto run_cache
if /I "%~1"=="rabbitmq" goto run_rabbitmq
if /I "%~1"=="admin-api" goto run_admin_api
if /I "%~1"=="portal-api" goto run_portal_api
if /I "%~1"=="admin-web" goto run_admin_web
if /I "%~1"=="store-web" goto run_store_web
if /I "%~1"=="check" goto check_only

echo Mall local development launcher
echo Workspace: "%ROOT%"
echo.
call :preflight
if errorlevel 1 goto failed
for %%P in (3307 6379 5672 8080 8085 5173 5174) do (
    call :port_open %%P
    if not errorlevel 1 (
        echo Port %%P is already in use. Close the existing local process before starting again.
        goto failed
    )
)
if exist Z:\ (
    echo Drive Z: is already in use. This launcher will not change an existing drive.
    goto failed
)
subst Z: "%ROOT%.tools"
if errorlevel 1 (
    echo Could not map the local tool directory to Z:.
    goto failed
)

start "Mall MySQL 3307" "%~f0" mysql
call :wait_port 3307 30
if errorlevel 1 (
    echo MySQL did not start. Check the Mall MySQL window.
    goto failed
)
start "Mall cache 6379" "%~f0" cache
call :wait_port 6379 30
if errorlevel 1 (
    echo Garnet did not start. Check the Mall cache window.
    goto failed
)
start "Mall RabbitMQ 5672" "%~f0" rabbitmq
call :wait_port 5672 60
if errorlevel 1 (
    echo RabbitMQ did not start. Check the Mall RabbitMQ window.
    goto failed
)
start "Mall admin API 8080" "%~f0" admin-api
start "Mall portal API 8085" "%~f0" portal-api
call :wait_port 8080 60
if errorlevel 1 echo Admin API did not open port 8080; check its window.
call :wait_port 8085 60
if errorlevel 1 echo Portal API did not open port 8085; check its window.

start "Mall admin web 5173" "%~f0" admin-web
start "Mall mobile web 5174" "%~f0" store-web
call :wait_port 5173 60
if errorlevel 1 echo Admin web did not open port 5173; check its window.
call :wait_port 5174 90
if errorlevel 1 echo Mobile web did not open port 5174; check its window.

echo.
echo Admin web:  http://127.0.0.1:5173/
echo Mobile web: http://127.0.0.1:5174/
echo Admin API:  http://127.0.0.1:8080/swagger-ui/index.html
echo Portal API: http://127.0.0.1:8085/swagger-ui/index.html
echo Each service runs in its own visible window. Close those windows to stop it.
echo Local test database only; real payment remains disabled.
start "" "http://127.0.0.1:5173/"
start "" "http://127.0.0.1:5174/"
pause
exit /b 0

:check_only
call :preflight
exit /b %errorlevel%

:preflight
if not exist "%JDK%\bin\java.exe" (echo Missing local JDK 17.& exit /b 1)
if not exist "%MYSQL%" (echo Missing local MySQL binary.& exit /b 1)
if not exist "%ROOT%.tools\mysql-data\auto.cnf" (echo Missing initialized local MySQL data directory.& exit /b 1)
if not exist "%GARNET%" (echo Missing local Garnet binary.& exit /b 1)
if not exist "%RABBIT%" (echo Missing local RabbitMQ binary.& exit /b 1)
if not exist "%ERLANG%\bin\erl.exe" (echo Missing local Erlang runtime.& exit /b 1)
if not exist "%ROOT%.tools\rabbitmq-test-password.txt" (echo Missing local RabbitMQ test credential.& exit /b 1)
if not exist "%ROOT%.tools\dotnet\dotnet.exe" (echo Missing local .NET runtime for Garnet.& exit /b 1)
if not exist "%ROOT%.tools\mysql-app-client.ini" (echo Missing local test database credentials.& exit /b 1)
if not exist "%ROOT%mall\mall-admin\target\mall-admin-1.0-SNAPSHOT.jar" (echo Missing admin API JAR.& exit /b 1)
if not exist "%ROOT%mall\mall-portal\target\mall-portal-1.0-SNAPSHOT.jar" (echo Missing portal API JAR.& exit /b 1)
if not exist "%ROOT%mall-admin-web\node_modules\.bin\vite.cmd" (echo Missing admin web dependencies.& exit /b 1)
if not exist "%ROOT%mall-app-web\node_modules\.bin\uni.cmd" (echo Missing mobile web dependencies.& exit /b 1)
where node.exe >nul 2>&1
if errorlevel 1 (
    if exist "%NODE_BUNDLED%\node.exe" (
        set "PATH=%NODE_BUNDLED%;%PATH%"
    ) else (
        echo Node.js is not in PATH and no bundled runtime was found.
        exit /b 1
    )
)
echo Dependency check OK.
exit /b 0

:port_open
powershell -NoProfile -Command "$c=New-Object Net.Sockets.TcpClient; try {$c.Connect('127.0.0.1',%~1); exit 0} catch {exit 1} finally {$c.Dispose()}" >nul 2>&1
exit /b %errorlevel%

:wait_port
set /a attempt=0
:wait_again
call :port_open %~1
if not errorlevel 1 exit /b 0
set /a attempt+=1 >nul
if %attempt% GEQ %~2 exit /b 1
powershell -NoProfile -Command "Start-Sleep -Seconds 1" >nul
goto wait_again

:load_db_password
set "DBPASS="
for /f "usebackq tokens=1,* delims==" %%A in ("%ROOT%.tools\mysql-app-client.ini") do if /I "%%A"=="password" set "DBPASS=%%B"
if not defined DBPASS (echo Test DB password not found.& exit /b 1)
exit /b 0

:set_java_db_env
call :load_db_password
if errorlevel 1 exit /b 1
set "JAVA_HOME=%JDK%"
set "PATH=%JDK%\bin;%PATH%"
set "SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3307/mall_phase3a?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
set "SPRING_DATASOURCE_USERNAME=mall_test"
set "SPRING_DATASOURCE_PASSWORD=%DBPASS%"
set "DBPASS="
exit /b 0

:run_mysql
echo Starting local MySQL on 127.0.0.1:3307 ...
"Z:\mysql\mysql-8.4.11-winx64\bin\mysqld.exe" --no-defaults --basedir=Z:\mysql\mysql-8.4.11-winx64 --datadir=Z:\mysql-data --port=3307 --bind-address=127.0.0.1 --mysqlx=OFF
echo MySQL exited with code %errorlevel%.
pause
exit /b 1

:run_cache
set "DOTNET_ROOT=%ROOT%.tools\dotnet"
set "PATH=%DOTNET_ROOT%;%PATH%"
echo Starting Garnet Redis-compatible cache on 127.0.0.1:6379 ...
"%GARNET%" --port 6379 --bind 127.0.0.1 --memory 128m
echo Cache exited with code %errorlevel%.
pause
exit /b 1

:run_rabbitmq
set "ERLANG_HOME=Z:\erlang\otp-27.3.4.14"
set "RABBITMQ_BASE=Z:\rabbitmq-data"
set "RABBITMQ_CONFIG_FILE=Z:\rabbitmq-data\rabbitmq.conf"
set "RABBITMQ_NODENAME=rabbit@localhost"
echo Starting local RabbitMQ on 127.0.0.1:5672 ...
call "%RABBIT%"
echo RabbitMQ exited with code %errorlevel%.
pause
exit /b 1

:run_admin_api
call :set_java_db_env
if errorlevel 1 goto failed
cd /d "%ROOT%mall"
echo Starting admin API on 127.0.0.1:8080 ...
"%JDK%\bin\java.exe" -jar "mall-admin\target\mall-admin-1.0-SNAPSHOT.jar" --server.address=127.0.0.1
echo Admin API exited with code %errorlevel%.
pause
exit /b 1

:run_portal_api
call :set_java_db_env
if errorlevel 1 goto failed
set "RABBITPASS="
for /f "usebackq delims=" %%A in ("%ROOT%.tools\rabbitmq-test-password.txt") do set "RABBITPASS=%%A"
if not defined RABBITPASS (echo RabbitMQ test password not found.& goto failed)
set "SPRING_RABBITMQ_USERNAME=mall_test"
set "SPRING_RABBITMQ_PASSWORD=%RABBITPASS%"
set "SPRING_RABBITMQ_VIRTUAL_HOST=/mall"
set "RABBITPASS="
call :port_open 5672
if errorlevel 1 (echo RabbitMQ is unavailable; order creation will return 503.& set "SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP=false") else (set "SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP=true")
set "MALL_PAYMENT_MOCK_ENABLED=true"
cd /d "%ROOT%mall"
echo Starting portal API on 127.0.0.1:8085 ...
"%JDK%\bin\java.exe" -jar "mall-portal\target\mall-portal-1.0-SNAPSHOT.jar" --server.address=127.0.0.1
echo Portal API exited with code %errorlevel%.
pause
exit /b 1

:run_admin_web
cd /d "%ROOT%mall-admin-web"
echo Starting admin web on 127.0.0.1:5173 ...
call "node_modules\.bin\vite.cmd" --host 127.0.0.1 --port 5173 --strictPort
echo Admin web exited with code %errorlevel%.
pause
exit /b 1

:run_store_web
cd /d "%ROOT%mall-app-web"
set "VITE_USE_ALIPAY=false"
echo Starting mobile H5 web on 127.0.0.1:5174 ...
call "node_modules\.bin\uni.cmd" --host 127.0.0.1 --port 5174 --strictPort
echo Mobile web exited with code %errorlevel%.
pause
exit /b 1

:failed
echo Startup stopped. Existing project files were not changed.
pause
exit /b 1

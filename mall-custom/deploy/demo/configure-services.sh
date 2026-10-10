#!/usr/bin/env bash
set -euo pipefail

if id mall-demo >/dev/null 2>&1 || test -e /opt/mall-demo/mall-admin.jar; then
  echo 'demo service already configured; refusing to overwrite it' >&2
  exit 1
fi

useradd --system --home-dir /opt/mall-demo --shell /usr/sbin/nologin mall-demo
install -d -m 755 /opt/mall-demo /opt/mall-demo/admin /opt/mall-demo/storefront
install -d -o mall-demo -g mall-demo -m 750 /var/log/mall-demo
install -m 644 /root/mall-demo-staging/unpack/mall-admin-1.0-SNAPSHOT.jar /opt/mall-demo/mall-admin.jar
install -m 644 /root/mall-demo-staging/unpack/mall-portal-1.0-SNAPSHOT.jar /opt/mall-demo/mall-portal.jar
cp -a /root/mall-demo-staging/unpack/dist/. /opt/mall-demo/admin/
cp -a /root/mall-demo-staging/unpack/h5/. /opt/mall-demo/storefront/

if rabbitmqctl list_vhosts -q | grep -Fxq /mall; then
  echo 'RabbitMQ /mall vhost already exists; refusing to overwrite it' >&2
  exit 1
fi
rabbit_password="$(openssl rand -hex 24)"
rabbitmqctl add_vhost /mall >/dev/null
rabbitmqctl add_user mall_demo "$rabbit_password" >/dev/null
rabbitmqctl set_permissions -p /mall mall_demo '.*' '.*' '.*' >/dev/null
rabbitmqctl delete_user guest >/dev/null

db_password="$(sed -n 's/^SPRING_DATASOURCE_PASSWORD=//p' /etc/mall-demo/db-password.env)"
admin_jwt="$(openssl rand -hex 48)"
portal_jwt="$(openssl rand -hex 48)"

cat > /etc/mall-demo/admin.env <<ENV
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/mall_demo?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false
SPRING_DATASOURCE_USERNAME=mall_demo
SPRING_DATASOURCE_PASSWORD=$db_password
SPRING_DATA_REDIS_HOST=127.0.0.1
JWT_SECRET=$admin_jwt
MALL_ADMIN_REGISTRATION_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
SPRINGDOC_API_DOCS_ENABLED=false
SPRING_DATASOURCE_DRUID_STAT_VIEW_SERVLET_ENABLED=false
LOGGING_LEVEL_COM_MACRO_MALL=info
ENV

cat > /etc/mall-demo/portal.env <<ENV
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/mall_demo?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false
SPRING_DATASOURCE_USERNAME=mall_demo
SPRING_DATASOURCE_PASSWORD=$db_password
SPRING_DATA_REDIS_HOST=127.0.0.1
SPRING_RABBITMQ_HOST=127.0.0.1
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_VIRTUAL_HOST=/mall
SPRING_RABBITMQ_USERNAME=mall_demo
SPRING_RABBITMQ_PASSWORD=$rabbit_password
JWT_SECRET=$portal_jwt
MALL_PAYMENT_ALIPAY_ENABLED=false
MALL_PAYMENT_MOCK_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
SPRINGDOC_API_DOCS_ENABLED=false
SPRING_DATASOURCE_DRUID_STAT_VIEW_SERVLET_ENABLED=false
LOGGING_LEVEL_COM_MACRO_MALL=info
ENV

chown root:mall-demo /etc/mall-demo/admin.env /etc/mall-demo/portal.env
chmod 640 /etc/mall-demo/admin.env /etc/mall-demo/portal.env
chmod 700 /etc/mall-demo
unset db_password rabbit_password admin_jwt portal_jwt
echo 'Dedicated demo files, user, and credentials configured; secrets were not printed.'

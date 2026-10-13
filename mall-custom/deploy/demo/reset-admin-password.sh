#!/usr/bin/env bash
# 重置平台管理员 admin 的 BCrypt 密码（默认演示密码 admin123）。
#
# 背景: 演示服务器上 ums_admin.admin 的密码哈希被改动后 admin/admin123 无法登录，
#       后台没有免登录重置入口（/admin/register 关闭、/admin/updatePassword 需要登录），
#       只能在服务器本机改库。管理员登录每次实时读库校验，不依赖 Redis 缓存，改完即可登录。
#
# 用法（在演示服务器以 root 执行）:
#   bash reset-admin-password.sh                  # 重置为默认演示密码 admin123
#   bash reset-admin-password.sh '<bcrypt 哈希>'  # 使用自定义 $2a$/$2b$/$2y$ 哈希
#
# 只更新 password 与 status 两列，不改动 ID、角色关联和其它管理员账号。
set -euo pipefail

# bcrypt(admin123, cost 10)
DEFAULT_HASH='$2a$10$bd7hUHHwJzt3CYN5W3/.UOQweHzZZZMzi3s0Bsrtf9IUkl2SYvP7y'

hash="${1:-$DEFAULT_HASH}"
case "$hash" in
  '$2a$'*|'$2b$'*|'$2y$'*) ;;
  *) echo '密码哈希必须以 $2a$ / $2b$ / $2y$ 开头' >&2; exit 2 ;;
esac

env_file=/etc/mall-demo/db-password.env
[ -r "$env_file" ] || { echo "读不到 $env_file（需 root 执行）" >&2; exit 3; }
db_password="$(sed -n 's/^SPRING_DATASOURCE_PASSWORD=//p' "$env_file")"
[ -n "$db_password" ] || { echo "$env_file 中没有 SPRING_DATASOURCE_PASSWORD" >&2; exit 4; }

export MYSQL_PWD="$db_password"
count="$(mysql -umall_demo -h127.0.0.1 -NBe "SELECT COUNT(*) FROM mall_demo.ums_admin WHERE username='admin'")"
if [ "$count" != 1 ]; then
  echo "mall_demo.ums_admin 中 username='admin' 的记录数为 $count，已中止，未做任何修改" >&2
  exit 5
fi

mysql -umall_demo -h127.0.0.1 mall_demo <<SQL
UPDATE ums_admin SET password='${hash}', status=1 WHERE username='admin';
SQL

mysql -umall_demo -h127.0.0.1 -NBe "SELECT CONCAT('admin status=', status, ' hash_prefix=', LEFT(password, 4)) FROM mall_demo.ums_admin WHERE username='admin'"
echo 'admin 密码已重置；未回显明文口令。请用新密码在 https://122.51.105.107/admin/ 登录验证。'

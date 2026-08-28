#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repository_root"

if [[ -f deploy/.env ]]; then
  set -a
  source deploy/.env
  set +a
fi

: "${MYSQL_ROOT_PASSWORD:?请在 deploy/.env 中配置 VM MySQL root 密码}"

vm_mysql_host="${VM_MYSQL_TUNNEL_HOST:-127.0.0.1}"
vm_mysql_port="${VM_MYSQL_TUNNEL_PORT:-13306}"
vm_mysql_user="${VM_MYSQL_ADMIN_USER:-root}"
run_suffix="$(date -u +%Y%m%d%H%M%S)_$$"
created_databases=()

mysql_admin() {
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
    --protocol=TCP \
    --host="$vm_mysql_host" \
    --port="$vm_mysql_port" \
    --user="$vm_mysql_user" \
    --connect-timeout=5 \
    "$@"
}

cleanup() {
  for database_name in "${created_databases[@]}"; do
    mysql_admin --execute="DROP DATABASE IF EXISTS \`$database_name\`;" >/dev/null || true
  done
}
trap cleanup EXIT

run_test_class() {
  local test_class="$1"
  local database_name="hengpick_vm_test_${run_suffix}_${2}"
  if [[ ! "$database_name" =~ ^[a-zA-Z0-9_]+$ ]]; then
    printf '%s\n' '测试数据库名称不合法' >&2
    exit 1
  fi

  mysql_admin --execute="CREATE DATABASE \`$database_name\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
  created_databases+=("$database_name")

  VM_DATABASE_INTEGRATION=true \
  MYSQL_URL="jdbc:mysql://${vm_mysql_host}:${vm_mysql_port}/${database_name}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true" \
  MYSQL_USERNAME="$vm_mysql_user" \
  MYSQL_PASSWORD="$MYSQL_ROOT_PASSWORD" \
  REDIS_URL="redis://127.0.0.1:${VM_REDIS_TUNNEL_PORT:-16379}" \
    mvn -f services/commerce-api/pom.xml \
      -Pvm-database-integration \
      -Dtest="$test_class" \
      test
}

run_test_class DatabaseMigrationIntegrationTest migration
run_test_class AuthMapperIntegrationTest identity
run_test_class CatalogMapperIntegrationTest catalog
run_test_class OfferMapperIntegrationTest pricing

printf '%s\n' 'VM 数据库迁移与 Mapper 集成测试全部通过。'

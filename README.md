# HengPick Smart Mall

HengPick Smart Mall 是一个 RN 商城宿主 + 独立 H5 + Spring Boot + FastAPI 的 Monorepo。当前已包含四个可独立启动的最小应用、外部 API OpenAPI 单一事实源，以及 P01-S03 的 HostBridge Schema 与跨端 Design Token 生成门禁；没有商品、价格、评分、Agent Graph、数据库或 WebView Bridge 实现。

## 环境要求

- Node.js 22.13+（Expo SDK 57 的最低要求）
- Java 21+
- Maven 3.6.3+
- Python 3.12+
- iOS 启动需要 Xcode；RN 不运行在 Docker 中

先执行只读检查：

```bash
./scripts/check-toolchain.sh
```

## 首次安装

```bash
make install
```

`npm ci` 使用根目录 `package-lock.json` 安装 RN/H5 依赖；Python 依赖按 `services/agent-service/requirements.lock` 安装在项目内 `.venv`。Spring Boot 依赖由 Maven BOM 锁定，并在首次运行或测试时获取。

## P02-S01 基础设施（VMware）

本项目的 MySQL、Redis、Qdrant 在 VMware VM 中运行；应用服务仍在开发机启动。基础设施模板在 [`deploy/docker-compose.yml`](./deploy/docker-compose.yml)，真实密码只保留在 VM 的环境文件中。

开发机不运行项目数据库容器。所有数据库、Redis 和 Qdrant 操作统一通过常驻 SSH 隧道访问 VM 内 Docker 容器：

```bash
ssh -N \
  -L 13306:127.0.0.1:3306 \
  -L 16379:127.0.0.1:6379 \
  -L 16333:127.0.0.1:6333 \
  -L 16334:127.0.0.1:6334 \
  root@192.168.234.130
```

该 SSH 进程应在后台常驻。开发机固定使用 `127.0.0.1:13306/16379/16333/16334`，业务代码和测试不得写入 VM 地址；隧道未建立时数据库相关命令应直接失败，不回退到本机数据库。

VM 运行项目自己的 Compose。首次部署在 VM 将旧 `/opt/dev-env/docker-compose.yml` 停止（不删除卷）并保留为 `docker-compose.legacy.yml`，然后复制 [`deploy/docker-compose.yml`](./deploy/docker-compose.yml)、[`deploy/.env.example`](./deploy/.env.example)、[`deploy/infra-up`](./deploy/infra-up)、[`deploy/infra-down`](./deploy/infra-down)、[`deploy/infra-status`](./deploy/infra-status) 到 `/opt/dev-env`。将 `.env.example` 改名为 `.env` 并设置真实密码/API Key 后执行：

```bash
./infra-up
./infra-status
./infra-down
```

`infra-down` 只停止并移除容器和网络，默认保留 MySQL、Redis、Qdrant 命名卷；删除卷是显式的人工恢复操作，P02-S01 不提供该命令。容器 `healthy` 只说明依赖容器可接受连接；应用级 live/ready 留待 P02-S03。

## P02-S02 数据库迁移

Flyway 的迁移文件位于 `services/commerce-api/src/main/resources/db/migration/`，只能新增版本，不修改已应用迁移。执行迁移时由调用环境提供数据库连接变量，真实密码不写入仓库：

```bash
export MYSQL_URL='jdbc:mysql://127.0.0.1:13306/hengpick_mall?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
export MYSQL_USERNAME='<migration-user>'
export MYSQL_PASSWORD='<migration-password>'
make db-migrate
```

导入经校验的固定模拟数据时执行：

```bash
make db-seed
```

该命令先运行 Flyway，再按 `Category → Product → SKU → Shop → Offer → Review` 顺序在一个事务内幂等导入；任何步骤失败都会回滚本次导入。

首批迁移只创建 Identity 和 Catalog 基表；金额/报价、业务数据和 API 仍未实现。MySQL 以 UTC `DATETIME(3)` 存时间、`CHAR(26)` 存 ULID；后续业务金额使用 `DECIMAL(12,2)` 和 Java `BigDecimal`。

服务需要连接数据库时，显式启用 `database` profile 并提供同一组 `MYSQL_*` 变量和 `REDIS_URL`；任一变量缺失即启动失败。默认 profile 保持 P01 的独立启动行为。

默认 Java 测试不访问数据库。VM 数据库集成测试会通过 SSH 隧道，在 VM MySQL 中分别创建一次性迁移、Catalog 和 Pricing 测试库，执行后自动删除；不会触碰业务库：

```bash
make test-db-integration
```

该命令读取未提交的 `deploy/.env` 中的 VM MySQL 管理密码。禁止把测试库改成已有业务库，也不再以宿主机 Docker/Testcontainers 作为本项目数据库验收入口。

## 独立启动四个应用

先复制并填写根目录 `.env`（不要提交），然后将变量导入当前终端；应用不会自动读取 `.env`，这是为了避免将密钥隐式带入客户端或子进程：

```bash
cp .env.example .env
# 编辑 .env，填入 VM 地址和真实密码；随后在每个服务终端执行：
set -a && source .env && set +a
```

在四个终端分别运行：

```bash
npm run start:commerce-app
npm run start:smart-mall-h5
npm run start:commerce-api
npm run start:agent-service
```

- RN/Expo：终端出现 Metro 后按 `i` 打开 iOS Simulator，或用 Expo 开发工具选择目标。
- H5：[http://localhost:5173](http://localhost:5173)，静态健康文件为 [http://localhost:5173/health.json](http://localhost:5173/health.json)。
- Commerce API：[http://localhost:8080](http://localhost:8080)。`/actuator/health/liveness` 只代表进程存活；`/actuator/health/readiness` 在 `database` profile 中检查 MySQL、Redis，并以 `agentAvailability=DEGRADED` 标记 Agent 不可用而不阻断传统商城。
- Agent Service：[http://localhost:8000](http://localhost:8000)。`/health/live` 只检查进程；`/health/ready` 检查 Qdrant、Commerce Tool API 和模型配置，失败返回 503。

端口可分别通过 `COMMERCE_PORT`、`AGENT_PORT` 修改；H5 可用 `-- --port <port>` 覆盖 Vite 端口。四端当前互不调用，因此任何一个未启动都不应阻止其他应用显示最小页面或 health。

## 验证

```bash
npm run check
```

该命令先校验 OpenAPI 及 TS/Python 生成物没有漂移，再执行工作区结构测试、RN/H5 基础测试与格式检查、Spring Boot 测试、FastAPI 测试和 Python 静态检查。契约的正常修改流程为：

```bash
npm run contract:validate
npm run contract:generate
npm run contract:check
npm run bridge:validate
npm run bridge:generate
npm run bridge:check
npm run tokens:validate
npm run tokens:generate
npm run tokens:check
```

`packages/api-contracts/openapi.yaml` 是唯一手写外部 API 契约；`generated/` 下的类型不得手改。更多说明见 [`packages/api-contracts/README.md`](./packages/api-contracts/README.md)。其他测试也可分别执行：

```bash
npm test
npm run test:commerce-api
npm run test:agent-service
```

## 当前边界

P02-S03 只建立健康、配置和关联日志基础：尚未实现业务 API、数据版本校验、OpenTelemetry 导出、Agent Client 或 Tool API。Bridge 当前仅包含 Envelope、`bridge.ready`、`bridge.bootstrap`、`openProduct`；真实 WebView 消息通道、握手、Ticket 兑换、导航、超时与时钟窗口检查属于 P07/P08。Design Token 只生成 RN 常量和 H5 CSS Variables，尚未建设主题系统、组件库或业务 UI。外部 API 仍保持 P01-S02 的 `/api/v1/health` 与标准信封边界。

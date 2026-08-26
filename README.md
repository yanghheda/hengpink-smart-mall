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

## 独立启动四个应用

在四个终端分别运行：

```bash
npm run start:commerce-app
npm run start:smart-mall-h5
npm run start:commerce-api
npm run start:agent-service
```

- RN/Expo：终端出现 Metro 后按 `i` 打开 iOS Simulator，或用 Expo 开发工具选择目标。
- H5：[http://localhost:5173](http://localhost:5173)，静态健康文件为 [http://localhost:5173/health.json](http://localhost:5173/health.json)。
- Commerce API：[http://localhost:8080](http://localhost:8080)，健康端点为 [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)。
- Agent Service：[http://localhost:8000](http://localhost:8000)，存活端点为 [http://localhost:8000/health/live](http://localhost:8000/health/live)。

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

P01-S03 只建立 Bridge/Token 的单一事实源和生成漂移门禁。Bridge 当前仅包含 Envelope、`bridge.ready`、`bridge.bootstrap`、`openProduct`；真实 WebView 消息通道、握手、Ticket 兑换、导航、超时与时钟窗口检查属于 P07/P08。Design Token 只生成 RN 常量和 H5 CSS Variables，尚未建设主题系统、组件库或业务 UI。外部 API 仍保持 P01-S02 的 `/api/v1/health` 与标准信封边界。

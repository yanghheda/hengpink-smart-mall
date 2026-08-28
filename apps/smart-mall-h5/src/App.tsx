import { useEffect, useMemo, useState } from "react";

import type { HostBridge } from "./bridge/hostBridge";
import { createHostBridgeReactNative } from "./bridge/hostBridgeReactNative";
import { createHostBridgeWeb } from "./bridge/hostBridgeWeb";
import "./styles.css";

const quickScenarios = [
  "给父母买手机，预算 3000 元，重视易用和续航",
  "宿舍用空气净化器，晚上安静，预算 1500 元",
  "程序员办公显示器，写代码为主，偶尔修图",
] as const;

function currentPath() {
  return `${window.location.pathname}${window.location.search}`;
}

function parsePreview(path: string) {
  const url = new URL(path, "https://standalone.hengpick.local");
  const match = url.pathname.match(/^\/standalone\/products\/([^/]+)$/);
  if (!match) return undefined;
  return {
    productId: decodeURIComponent(match[1]),
    skuId: url.searchParams.get("skuId") ?? "未指定",
  };
}

function ProductPreview({ path }: { path: string }) {
  const product = parsePreview(path);
  if (!product) return null;
  return (
    <main className="shell">
      <section className="panel preview-panel">
        <p className="eyebrow">STANDALONE DEMO · 商品预览</p>
        <h1>商品预览</h1>
        <p className="notice">项目模拟数据</p>
        <dl className="resource-list">
          <div>
            <dt>Product ID</dt>
            <dd>{product.productId}</dd>
          </div>
          <div>
            <dt>SKU ID</dt>
            <dd>{product.skuId}</dd>
          </div>
        </dl>
        <p className="muted">
          本阶段未接入商品详情接口，因此不展示商品名、价格或评分。
        </p>
        <a className="secondary-action" href="/standalone">
          返回智能商城首页
        </a>
      </section>
    </main>
  );
}

type NativeWindow = Window & {
  ReactNativeWebView?: { postMessage(raw: string): void };
};

function Home({ onPathChange }: { onPathChange: (path: string) => void }) {
  const [draft, setDraft] = useState("");
  const [message, setMessage] = useState("");
  const [bridgeStatus, setBridgeStatus] = useState("Standalone Demo");
  const bridge = useMemo((): HostBridge => {
    if (typeof window === "undefined")
      return createHostBridgeWeb({ navigate: onPathChange });
    const nativeBridge = (window as NativeWindow).ReactNativeWebView;
    if (!nativeBridge)
      return createHostBridgeWeb({
        navigate(path) {
          window.history.pushState({}, "", path);
          onPathChange(path);
        },
      });
    return createHostBridgeReactNative({
      postMessage: (raw) => nativeBridge.postMessage(raw),
      exchangeTicket: async (ticket) => {
        const response = await fetch(
          `${import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080"}/api/v1/smart-mall/sessions/exchange`,
          {
            method: "POST",
            headers: { "content-type": "application/json" },
            body: JSON.stringify({
              ticket,
              hostType: "REACT_NATIVE",
              deviceSessionId:
                import.meta.env.VITE_DEVICE_SESSION_ID ??
                "commerce-app-local-device",
              bridgeVersion: "1.0",
            }),
          },
        );
        const payload = await response.json();
        if (!response.ok)
          throw new Error(payload.error?.message ?? "Ticket 已过期或无法兑换");
        return payload.data;
      },
      onSnapshot(snapshot) {
        if (snapshot.status === "initialized") setBridgeStatus("App 已连接");
        if (snapshot.status === "standalone")
          setBridgeStatus("当前在独立演示模式");
        if (snapshot.status === "error")
          setBridgeStatus(snapshot.error ?? "宿主初始化失败");
      },
    });
  }, [onPathChange]);
  useEffect(() => {
    if (!("start" in bridge) || !("receive" in bridge)) return;
    const nativeBridge = bridge as HostBridge & {
      start(): void;
      stop(): void;
      receive(raw: string): Promise<void>;
    };
    const receive = (event: MessageEvent) => {
      if (typeof event.data === "string") void nativeBridge.receive(event.data);
    };
    nativeBridge.start();
    window.addEventListener("message", receive);
    document.addEventListener("message", receive as EventListener);
    return () => {
      window.removeEventListener("message", receive);
      document.removeEventListener("message", receive as EventListener);
      nativeBridge.stop();
    };
  }, [bridge]);
  return (
    <main className="shell">
      <section className="panel">
        <div className="topline">
          <p className="eyebrow">HENGPICK SMART MALL</p>
          <span className="mode-badge">{bridgeStatus}</span>
        </div>
        <h1>告诉我为谁买、怎么用</h1>
        <p className="description">
          我会把购买目标整理成可核对的条件；决策会话将在后续阶段接入。
        </p>
        <p className="notice">所有商品、价格与评价均为项目模拟数据</p>
        <form
          className="decision-form"
          onSubmit={(event) => {
            event.preventDefault();
            setMessage(
              draft.trim()
                ? "需求已保留在当前页面；决策服务将在后续阶段接入。"
                : "请先描述你的购买目标。",
            );
          }}
        >
          <label htmlFor="decision-input">描述你的购买目标</label>
          <textarea
            id="decision-input"
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="例如：给爸妈买一台 3000 元内、系统简单、续航好的手机"
          />
          <button type="submit">开始分析</button>
        </form>
        {message ? <p className="status-message">{message}</p> : null}
        <section aria-labelledby="quick-title" className="quick-section">
          <h2 id="quick-title">快捷场景</h2>
          <div className="quick-list">
            {quickScenarios.map((scenario, index) => (
              <button
                className="quick-button"
                key={scenario}
                onClick={() => setDraft(scenario)}
                type="button"
              >
                {index === 0 ? "给父母买手机" : scenario}
              </button>
            ))}
          </div>
        </section>
        <section className="preview-card">
          <div>
            <p className="eyebrow">ADAPTER CHECK</p>
            <h2>商品打开闭环</h2>
            <p className="muted">只传资源 ID，不在前端伪造商品事实。</p>
          </div>
          <button
            className="secondary-action"
            type="button"
            onClick={() => {
              void bridge
                .openProduct({
                  productId: "P-PIXEL-9A",
                  skuId: "S-PIXEL-9A-256-W",
                })
                .catch((error: unknown) =>
                  setMessage(
                    error instanceof Error ? error.message : "商品打开失败",
                  ),
                );
            }}
          >
            打开预览
          </button>
        </section>
      </section>
    </main>
  );
}

export function App({ initialPath }: { initialPath?: string }) {
  const [path, setPath] = useState(initialPath ?? currentPath);
  return parsePreview(path) ? (
    <ProductPreview path={path} />
  ) : (
    <Home onPathChange={setPath} />
  );
}

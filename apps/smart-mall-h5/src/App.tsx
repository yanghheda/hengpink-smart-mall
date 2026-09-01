import { useEffect, useMemo, useRef, useState } from "react";

import type { HostBridge } from "./bridge/hostBridge";
import { createHostBridgeReactNative } from "./bridge/hostBridgeReactNative";
import { createHostBridgeWeb } from "./bridge/hostBridgeWeb";
import {
  loadDecisionTrace,
  readH5AccessToken,
  rememberH5AccessToken,
  type DecisionTrace,
} from "./decision/decisionTrace";
import { startDecision } from "./decision/startDecision";
import { createStandaloneH5Session } from "./decision/standaloneSession";
import {
  loadDecisionReport,
  reweightDecisionReport,
  type DecisionReport,
} from "./decision/decisionReport";
import {
  consumeDecisionStream,
  fetchDecisionSessionSnapshot,
  recoverDecisionSession,
  type DecisionSessionSnapshot,
  type DecisionTransportState,
} from "./decision/decisionStream";
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

function parseTraceRunId(path: string) {
  const url = new URL(path, "https://standalone.hengpick.local");
  const match = url.pathname.match(/^\/admin\/decision-runs\/([^/]+)\/trace$/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

function Summary({ value }: { value: Record<string, unknown> }) {
  const entries = Object.entries(value);
  if (!entries.length) return <span className="muted">无公开摘要</span>;
  return (
    <ul className="summary-list">
      {entries.map(([key, item]) => (
        <li key={key}>
          <strong>{key}</strong>: {JSON.stringify(item)}
        </li>
      ))}
    </ul>
  );
}

function TracePage({
  runId,
  initialTrace,
}: {
  runId: string;
  initialTrace?: DecisionTrace;
}) {
  const [trace, setTrace] = useState(initialTrace);
  const [error, setError] = useState("");
  useEffect(() => {
    if (initialTrace) return;
    const accessToken = readH5AccessToken();
    if (!accessToken) {
      setError("缺少管理员会话，请从测试账号重新进入。");
      return;
    }
    void loadDecisionTrace(runId, accessToken)
      .then(setTrace)
      .catch((reason: unknown) =>
        setError(reason instanceof Error ? reason.message : "Trace 加载失败"),
      );
  }, [initialTrace, runId]);
  if (error)
    return (
      <main className="shell">
        <section className="panel">
          <h1>开发者 Trace</h1>
          <p className="error-banner">{error}</p>
        </section>
      </main>
    );
  if (!trace)
    return (
      <main className="shell">
        <section className="panel">
          <h1>开发者 Trace</h1>
          <p>正在读取脱敏快照…</p>
        </section>
      </main>
    );
  return (
    <main className="shell trace-shell">
      <section className="panel">
        <p className="eyebrow">DEMO_ADMIN · 只读脱敏快照</p>
        <h1>开发者 Trace</h1>
        <p className="notice">不展示系统提示词、模型原始响应或私有推理过程</p>
        <dl className="trace-grid">
          <div>
            <dt>Run</dt>
            <dd>
              {trace.runId} · v{trace.runVersion}
            </dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd>
              <span
                className={`trace-status trace-${trace.status.toLowerCase()}`}
              >
                {trace.status}
              </span>
            </dd>
          </div>
          <div>
            <dt>当前/失败节点</dt>
            <dd>{trace.activeNode ?? "—"}</dd>
          </div>
          <div>
            <dt>错误码</dt>
            <dd>{trace.failureCode ?? "—"}</dd>
          </div>
          <div>
            <dt>Token</dt>
            <dd>
              {trace.usage.tokenInput ?? 0} 入 / {trace.usage.tokenOutput ?? 0}{" "}
              出
            </dd>
          </div>
          <div>
            <dt>估算成本</dt>
            <dd>{trace.usage.estimatedCost ?? "未记录"}</dd>
          </div>
        </dl>
        <section className="trace-section">
          <h2>版本</h2>
          <div className="chip-list">
            {Object.entries(trace.versions)
              .filter(([, value]) => value)
              .map(([key, value]) => (
                <span className="trace-chip" key={key}>
                  {key}: {value}
                </span>
              ))}
          </div>
        </section>
        <section className="trace-section">
          <h2>降级</h2>
          <div className="chip-list">
            {trace.degradationCodes.length ? (
              trace.degradationCodes.map((code) => (
                <span className="trace-chip warning" key={code}>
                  {code}
                </span>
              ))
            ) : (
              <span className="muted">无降级</span>
            )}
          </div>
        </section>
        <section className="trace-section">
          <h2>节点时间轴</h2>
          <ol className="timeline">
            {trace.steps.map((step) => (
              <li key={step.sequence} className="timeline-item">
                <div className="timeline-title">
                  <strong>
                    {step.sequence}. {step.node}
                  </strong>
                  <span>
                    {step.status} · {step.durationMs} ms
                  </span>
                </div>
                {step.errorCode ? (
                  <p className="error-banner">{step.errorCode}</p>
                ) : null}
                {step.warningCodes.length ? (
                  <p className="muted">警告：{step.warningCodes.join("、")}</p>
                ) : null}
                <div className="trace-summaries">
                  <div>
                    <h3>输入摘要 / Tool</h3>
                    <Summary value={step.inputSummary} />
                  </div>
                  <div>
                    <h3>结果摘要 / Evidence</h3>
                    <Summary value={step.outputSummary} />
                  </div>
                </div>
              </li>
            ))}
          </ol>
        </section>
      </section>
    </main>
  );
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
  const [submitting, setSubmitting] = useState(false);
  const [snapshot, setSnapshot] = useState<DecisionSessionSnapshot>();
  const [progress, setProgress] = useState(0);
  const [stageText, setStageText] = useState("");
  const [transportState, setTransportState] =
    useState<DecisionTransportState>("STOPPED");
  const [report, setReport] = useState<DecisionReport>();
  const [weights, setWeights] = useState({
    NEED_MATCH: 1,
    PRICE_VALUE: 1,
    REVIEW_QUALITY: 1,
    PROMOTION_VALUE: 1,
    RELIABILITY: 1,
  });
  const [reweighting, setReweighting] = useState(false);
  const streamCursor = useRef<string | undefined>(undefined);
  const activeAbort = useRef<AbortController | undefined>(undefined);
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
        if (snapshot.accessToken) rememberH5AccessToken(snapshot.accessToken);
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
  useEffect(() => () => activeAbort.current?.abort(), []);

  const followDecision = async (
    started: { sessionId: string },
    accessToken: string,
  ) => {
    activeAbort.current?.abort();
    const controller = new AbortController();
    activeAbort.current = controller;
    streamCursor.current = undefined;
    const apiBaseUrl =
      import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080";
    const sessionUrl = `${apiBaseUrl}/api/v1/decision-sessions/${encodeURIComponent(started.sessionId)}`;
    const completed = await recoverDecisionSession({
      fetchSnapshot: () =>
        fetchDecisionSessionSnapshot({
          url: sessionUrl,
          accessToken,
          signal: controller.signal,
        }),
      consumeStream: () =>
        consumeDecisionStream({
          url: `${sessionUrl}/stream`,
          accessToken,
          lastEventId: streamCursor.current,
          signal: controller.signal,
          onEvent(event) {
            streamCursor.current = event.eventId;
            setProgress(event.progress);
            const displayText = event.payload.displayText;
            setStageText(
              typeof displayText === "string"
                ? displayText
                : `正在执行 ${event.eventType}`,
            );
          },
        }),
      onSnapshot: setSnapshot,
      onTransportState: setTransportState,
    });
    if (
      (completed.status === "COMPLETED" || completed.status === "PARTIAL") &&
      completed.currentReportVersion
    ) {
      setProgress(100);
      setStageText("购买分析已完成");
      setReport(
        await loadDecisionReport(
          completed.sessionId,
          completed.currentReportVersion,
          accessToken,
        ),
      );
    } else if (completed.status === "FAILED") {
      setStageText("分析失败，请稍后重试");
    } else if (completed.status === "WAITING_CLARIFICATION") {
      setStageText("需要补充信息后才能继续分析");
    }
  };
  return (
    <main className="shell">
      <section className="panel">
        <div className="topline">
          <p className="eyebrow">HENGPICK SMART MALL</p>
          <span className="mode-badge">{bridgeStatus}</span>
        </div>
        <h1>告诉我为谁买、怎么用</h1>
        <p className="description">
          我会把购买目标整理成可核对的条件，并启动可追踪的决策分析。
        </p>
        <p className="notice">所有商品、价格与评价均为项目模拟数据</p>
        <form
          className="decision-form"
          onSubmit={(event) => {
            event.preventDefault();
            const requirement = draft.trim();
            if (!requirement) {
              setMessage("请先描述你的购买目标。");
              return;
            }
            setSubmitting(true);
            setReport(undefined);
            setSnapshot(undefined);
            setProgress(0);
            setMessage("正在创建决策会话…");
            const existingToken = readH5AccessToken();
            const accessTokenPromise = existingToken
              ? Promise.resolve(existingToken)
              : window.location.pathname.startsWith("/standalone")
                ? createStandaloneH5Session()
                : Promise.reject(
                    new Error(
                      "当前没有 H5 会话，请从已登录的商城 App 进入智能商城。",
                    ),
                  );
            void accessTokenPromise
              .then((accessToken) =>
                startDecision({ requirement, accessToken }),
              )
              .then((started) => {
                setMessage(
                  `分析已启动：Session ${started.sessionId}，Run v${started.runVersion}`,
                );
                return accessTokenPromise.then((accessToken) =>
                  followDecision(started, accessToken),
                );
              })
              .catch((error: unknown) => {
                setMessage(
                  error instanceof Error ? error.message : "分析启动失败",
                );
              })
              .finally(() => setSubmitting(false));
          }}
        >
          <label htmlFor="decision-input">描述你的购买目标</label>
          <textarea
            id="decision-input"
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="例如：给爸妈买一台 3000 元内、系统简单、续航好的手机"
          />
          <button disabled={submitting} type="submit">
            {submitting ? "正在启动…" : "开始分析"}
          </button>
        </form>
        {message ? <p className="status-message">{message}</p> : null}
        {snapshot ? (
          <section className="analysis-progress" aria-live="polite">
            <div className="progress-title">
              <strong>{stageText || "正在读取决策状态"}</strong>
              <span>{progress}%</span>
            </div>
            <progress max="100" value={progress} />
            <p className="muted">
              状态：{snapshot.status} · 传输：{transportState} · Run v
              {snapshot.currentRunVersion}
            </p>
          </section>
        ) : null}
        {report ? (
          <section className="report-section" aria-labelledby="report-title">
            <div className="report-heading">
              <div>
                <p className="eyebrow">REPORT V{report.version}</p>
                <h2 id="report-title">推荐结果</h2>
              </div>
              <span className="mode-badge">{report.report.generationType}</span>
            </div>
            <p>{report.report.summary}</p>
            <ol className="recommendation-list">
              {report.report.recommendations.map((item) => (
                <li className="recommendation-card" key={item.skuId}>
                  <div className="recommendation-title">
                    <strong>
                      #{item.rank} · {item.productId}
                    </strong>
                    <span>{item.finalScore} 分</span>
                  </div>
                  <p className="price">¥{item.finalPrice}</p>
                  <p className="muted">SKU：{item.skuId}</p>
                  <ul>
                    {item.reasons.map((reason) => (
                      <li key={`${item.skuId}-${reason.text}`}>
                        {reason.text}
                        {reason.factIds.length ? (
                          <small>事实：{reason.factIds.join("、")}</small>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ol>
            {report.report.overallDataGaps.length ? (
              <p className="notice">
                数据缺口：{report.report.overallDataGaps.join("；")}
              </p>
            ) : null}
            <section className="weights-panel">
              <h3>调整关注重点</h3>
              {Object.entries(weights).map(([dimension, value]) => (
                <label className="weight-row" key={dimension}>
                  <span>{dimension}</span>
                  <input
                    type="range"
                    min="0"
                    max="10"
                    step="1"
                    value={value}
                    onChange={(event) =>
                      setWeights((current) => ({
                        ...current,
                        [dimension]: Number(event.target.value),
                      }))
                    }
                  />
                  <output>{value}</output>
                </label>
              ))}
              <button
                className="secondary-action"
                disabled={reweighting}
                type="button"
                onClick={() => {
                  const accessToken = readH5AccessToken();
                  if (!accessToken) {
                    setMessage("H5 会话已过期，请重新进入页面。");
                    return;
                  }
                  setReweighting(true);
                  void reweightDecisionReport(
                    report.sessionId,
                    report.version,
                    weights,
                    accessToken,
                  )
                    .then((result) =>
                      loadDecisionReport(
                        report.sessionId,
                        result.version,
                        accessToken,
                      ),
                    )
                    .then((updated) => {
                      setReport(updated);
                      setMessage(`已生成报告 V${updated.version}`);
                    })
                    .catch((error: unknown) =>
                      setMessage(
                        error instanceof Error ? error.message : "调权失败",
                      ),
                    )
                    .finally(() => setReweighting(false));
                }}
              >
                {reweighting ? "正在重排…" : "按新权重重排"}
              </button>
            </section>
          </section>
        ) : null}
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

export function App({
  initialPath,
  initialTrace,
}: {
  initialPath?: string;
  initialTrace?: DecisionTrace;
}) {
  const [path, setPath] = useState(initialPath ?? currentPath);
  const traceRunId = parseTraceRunId(path);
  if (traceRunId)
    return <TracePage runId={traceRunId} initialTrace={initialTrace} />;
  return parsePreview(path) ? (
    <ProductPreview path={path} />
  ) : (
    <Home onPathChange={setPath} />
  );
}

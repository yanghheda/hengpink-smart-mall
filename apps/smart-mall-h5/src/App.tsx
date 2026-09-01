import { useEffect, useMemo, useRef, useState } from "react";

import type { HostBridge } from "./bridge/hostBridge";
import { createHostBridgeReactNative } from "./bridge/hostBridgeReactNative";
import { createHostBridgeWeb } from "./bridge/hostBridgeWeb";
import {
  loadDecisionTrace,
  loadDecisionTraceList,
  clearAdminAccessToken,
  loginDemoAdmin,
  readAdminAccessToken,
  readH5AccessToken,
  rememberH5AccessToken,
  type DecisionTrace,
  type DecisionTraceListItem,
} from "./decision/decisionTrace";
import { continueDecision, startDecision } from "./decision/startDecision";
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

const weightLabels: Record<string, string> = {
  NEED_MATCH: "需求匹配度",
  PRICE_VALUE: "价格与性价比",
  REVIEW_QUALITY: "用户口碑",
  PROMOTION_VALUE: "优惠力度",
  RELIABILITY: "商品可靠性",
};

function reportGenerationLabel(generationType: string) {
  if (generationType === "VALIDATED_REPORT") return "AI 生成 · 事实已核验";
  if (generationType === "DETERMINISTIC_REWEIGHT") return "已按偏好重新排序";
  return "基础分析结果";
}

function formatScore(value: string | number) {
  const score = Number(value);
  return Number.isFinite(score) ? score.toFixed(2) : value;
}

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
  const [adminToken, setAdminToken] = useState(() => readAdminAccessToken());
  const [adminAccount, setAdminAccount] = useState("demo_admin");
  const [adminPassword, setAdminPassword] = useState("demo123456");
  const [loggingIn, setLoggingIn] = useState(false);
  useEffect(() => {
    if (initialTrace) return;
    if (!adminToken) return;
    setError("");
    void loadDecisionTrace(runId, adminToken)
      .then(setTrace)
      .catch((reason: unknown) => {
        clearAdminAccessToken();
        setAdminToken(undefined);
        setError(reason instanceof Error ? reason.message : "Trace 加载失败");
      });
  }, [adminToken, initialTrace, runId]);
  if (!adminToken)
    return (
      <main className="shell">
        <section className="panel trace-login-panel">
          <p className="eyebrow">DEMO_ADMIN · 开发调试</p>
          <h1>登录后查看 Trace</h1>
          <p className="muted">Trace 只展示脱敏执行摘要，不包含提示词和模型私有推理。</p>
          <form className="trace-login-form" onSubmit={(event) => {
            event.preventDefault();
            setLoggingIn(true);
            setError("");
            void loginDemoAdmin(adminAccount, adminPassword)
              .then(setAdminToken)
              .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "管理员登录失败"))
              .finally(() => setLoggingIn(false));
          }}>
            <label>管理员账号<input value={adminAccount} onChange={(event) => setAdminAccount(event.target.value)} /></label>
            <label>密码<input type="password" value={adminPassword} onChange={(event) => setAdminPassword(event.target.value)} /></label>
            {error ? <p className="error-banner">{error}</p> : null}
            <button className="secondary-action" disabled={loggingIn} type="submit">
              {loggingIn ? "正在登录…" : "登录并查看"}
            </button>
          </form>
        </section>
      </main>
    );
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

function TraceListPage() {
  const [items, setItems] = useState<DecisionTraceListItem[]>();
  const [error, setError] = useState("");
  const [adminToken, setAdminToken] = useState(() => readAdminAccessToken());
  const [loggingIn, setLoggingIn] = useState(false);
  useEffect(() => {
    if (!adminToken) return;
    setError("");
    void loadDecisionTraceList(adminToken).then(setItems).catch((reason: unknown) => {
      clearAdminAccessToken();
      setAdminToken(undefined);
      setError(reason instanceof Error ? reason.message : "Trace 列表加载失败");
    });
  }, [adminToken]);
  if (!adminToken) {
    return (
      <main className="shell">
        <section className="panel trace-login-panel">
          <p className="eyebrow">DEMO_ADMIN · 开发调试</p>
          <h1>登录后查看 Trace</h1>
          <form className="trace-login-form" onSubmit={(event) => {
            event.preventDefault();
            setLoggingIn(true);
            void loginDemoAdmin("demo_admin", "demo123456")
              .then(setAdminToken)
              .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "管理员登录失败"))
              .finally(() => setLoggingIn(false));
          }}>
            {error ? <p className="error-banner">{error}</p> : null}
            <button className="secondary-action" disabled={loggingIn} type="submit">
              {loggingIn ? "正在登录…" : "使用演示管理员登录"}
            </button>
          </form>
        </section>
      </main>
    );
  }
  return (
    <main className="shell trace-shell">
      <section className="panel">
        <div className="trace-list-heading">
          <div><p className="eyebrow">DEMO_ADMIN · 最近 50 条</p><h1>Trace 列表</h1></div>
          <button className="secondary-action" type="button" onClick={() => {
            clearAdminAccessToken();
            setAdminToken(undefined);
          }}>退出管理员</button>
        </div>
        {!items ? <p className="muted">正在读取 Trace…</p> : items.length ? (
          <div className="trace-list">
            {items.map((item) => (
              <a className="trace-list-item" href={`/admin/decision-runs/${encodeURIComponent(item.runId)}/trace`} key={item.runId}>
                <div>
                  <strong>{item.runId}</strong>
                  <span>Session {item.sessionId} · Run v{item.runVersion}</span>
                </div>
                <div className="trace-list-meta">
                  <span className={`trace-status trace-${item.status.toLowerCase()}`}>{item.status}</span>
                  <time>{new Date(item.startedAt).toLocaleString("zh-CN")}</time>
                  <small>{item.failureCode ?? item.activeNode ?? "—"}</small>
                </div>
              </a>
            ))}
          </div>
        ) : <p className="muted">暂无决策 Run</p>}
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
  const [submittedRequirement, setSubmittedRequirement] = useState("");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [snapshot, setSnapshot] = useState<DecisionSessionSnapshot>();
  const [, setProgress] = useState(0);
  const [stageText, setStageText] = useState("");
  const [chatMessages, setChatMessages] = useState<Array<{
    id: string;
    role: "assistant" | "user";
    text: string;
    options?: string[];
  }>>([]);
  const [activeQuestionId, setActiveQuestionId] = useState<string>();
  const [, setTransportState] =
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
      let question = "还需要补充一点信息，请直接在下方回复。";
      let options: string[] = [];
      try {
        const clarification = JSON.parse(completed.clarificationJson ?? "null") as { questions?: Array<{ text?: string; options?: string[] }> } | null;
        const first = clarification?.questions?.[0];
        if (first?.text) question = first.text;
        options = first?.options ?? [];
      } catch { /* 后端旧数据没有追问摘要时使用兜底文案。 */ }
      const questionId = `${completed.sessionId}-${completed.currentRunVersion}`;
      setChatMessages((current) => current.some((item) => item.id === questionId)
        ? current
        : [...current, { id: questionId, role: "assistant", text: question, options }]);
      setActiveQuestionId(questionId);
      setStageText("等待你选择");
    }
  };

  const sendContent = (content: string) => {
    const requirement = content.trim();
    if (!requirement || submitting) return;
    const isClarification = snapshot?.status === "WAITING_CLARIFICATION";
    setSubmitting(true);
    setReport(undefined);
    setProgress(0);
    if (isClarification) {
      setChatMessages((current) => [...current, {
        id: `user-${Date.now()}`,
        role: "user",
        text: requirement,
      }]);
      setActiveQuestionId(undefined);
      setStageText("正在结合你的选择继续分析");
    } else {
      setSubmittedRequirement(requirement);
      setChatMessages([]);
    }
    setDraft("");
    setMessage(isClarification ? "正在根据你的选择继续分析…" : "正在创建决策会话…");
    const existingToken = readH5AccessToken();
    const accessTokenPromise = existingToken
      ? Promise.resolve(existingToken)
      : window.location.pathname.startsWith("/standalone")
        ? createStandaloneH5Session()
        : Promise.reject(new Error("当前没有 H5 会话，请从已登录的商城 App 进入智能商城。"));
    void accessTokenPromise
      .then((accessToken) => isClarification && snapshot
        ? continueDecision({ sessionId: snapshot.sessionId, content: requirement, accessToken })
        : startDecision({ requirement, accessToken }))
      .then((started) => accessTokenPromise.then((accessToken) => followDecision(started, accessToken)))
      .catch((error: unknown) => setMessage(error instanceof Error ? error.message : "分析启动失败"))
      .finally(() => setSubmitting(false));
  };
  return (
    <main className="shell chat-shell">
      <section className="panel chat-panel">
        <header className="chat-header">
          <div className="ai-avatar" aria-hidden="true">
            ✦
          </div>
          <div className="chat-heading">
            <h1>衡选 AI 导购</h1>
            <p>
              <span className="online-dot" /> 在线 · 为你认真挑选
            </p>
          </div>
          <span className="mode-badge">{bridgeStatus}</span>
        </header>

        <div className="conversation" aria-live="polite">
          <div className="message-row assistant-row">
            <div className="message-avatar" aria-hidden="true">
              ✦
            </div>
            <div className="message-content">
              <span className="speaker">衡选 AI</span>
              <div className="message-bubble assistant-bubble">
                <h2>你好，我是你的 AI 导购</h2>
                <p>
                  告诉我为谁买、预算多少、最看重什么，我会结合商品参数和价格帮你认真比较。
                </p>
                <span className="data-notice">
                  商品、价格与评价均为项目模拟数据
                </span>
              </div>
            </div>
          </div>

          {!submittedRequirement ? (
            <section aria-labelledby="quick-title" className="quick-section">
              <h2 id="quick-title">你可以这样问</h2>
              <div className="quick-list">
                {quickScenarios.map((scenario, index) => (
                  <button
                    className="quick-button"
                    key={scenario}
                    onClick={() => setDraft(scenario)}
                    type="button"
                  >
                    <span>
                      {index === 0 ? "手机" : index === 1 ? "净化器" : "显示器"}
                    </span>
                    {scenario}
                  </button>
                ))}
              </div>
            </section>
          ) : null}

          {submittedRequirement ? (
            <div className="message-row user-row">
              <div className="message-content">
                <span className="speaker">你</span>
                <div className="message-bubble user-bubble">
                  {submittedRequirement}
                </div>
              </div>
              <div className="user-avatar" aria-hidden="true">
                我
              </div>
            </div>
          ) : null}

          {chatMessages.map((item) => (
            <div className={`message-row ${item.role === "user" ? "user-row" : "assistant-row"}`} key={item.id}>
              {item.role === "assistant" ? <div className="message-avatar" aria-hidden="true">✦</div> : null}
              <div className="message-content">
                <span className="speaker">{item.role === "user" ? "你" : "衡选 AI"}</span>
                <div className={`message-bubble ${item.role === "user" ? "user-bubble" : "assistant-bubble"}`}>
                  <p>{item.text}</p>
                  {item.role === "assistant" && item.id === activeQuestionId && item.options?.length ? (
                    <div className="clarification-options">
                      {item.options.map((option) => (
                        <button disabled={submitting} key={option} onClick={() => sendContent(option)} type="button">
                          {option}
                        </button>
                      ))}
                    </div>
                  ) : null}
                </div>
              </div>
              {item.role === "user" ? <div className="user-avatar" aria-hidden="true">我</div> : null}
            </div>
          ))}

          {message && !snapshot ? (
            <div className="message-row assistant-row">
              <div className="message-avatar" aria-hidden="true">
                ✦
              </div>
              <div className="message-content">
                <span className="speaker">衡选 AI</span>
                <div className="message-bubble assistant-bubble status-message">
                  {submitting ? (
                    <span className="typing-dots">
                      <i />
                      <i />
                      <i />
                    </span>
                  ) : null}
                  {message}
                </div>
              </div>
            </div>
          ) : null}

          {snapshot && snapshot.status === "RUNNING" ? (
            <div className="message-row assistant-row">
              <div className="message-avatar" aria-hidden="true">
                ✦
              </div>
              <div className="message-content">
                <span className="speaker">衡选 AI</span>
                <div className="analysis-status" role="status">
                  <span className="analysis-spark" aria-hidden="true">✦</span>
                  <span className="shimmer-text">{stageText || "正在理解你的需求"}</span>
                </div>
              </div>
            </div>
          ) : null}

          {snapshot?.status === "FAILED" ? (
            <div className="message-row assistant-row">
              <div className="message-avatar" aria-hidden="true">✦</div>
              <div className="message-content">
                <span className="speaker">衡选 AI</span>
                <div className="analysis-failed" role="alert">
                  <span aria-hidden="true">!</span>
                  分析没有完成，请重新发起一次
                </div>
              </div>
            </div>
          ) : null}

          {report ? (
            <div className="message-row assistant-row report-message">
              <div className="message-avatar" aria-hidden="true">
                ✦
              </div>
              <div className="message-content wide-message">
                <span className="speaker">衡选 AI</span>
                <section
                  className="message-bubble assistant-bubble report-section"
                  aria-labelledby="report-title"
                >
                  <div className="report-heading">
                    <div>
                      <p className="eyebrow">
                        已为你比较完成 · REPORT V{report.version}
                      </p>
                      <h2 id="report-title">这几款更适合你</h2>
                    </div>
                    <span className="mode-badge">
                      {reportGenerationLabel(report.report.generationType)}
                    </span>
                  </div>
                  <p className="report-summary">{report.report.summary}</p>
                  <ol className="recommendation-list">
                    {report.report.recommendations.map((item) => (
                      <li className="recommendation-card" key={item.skuId}>
                        <div className="product-visual">
                          <span>{item.rank}</span>
                          <strong>HENGPICK</strong>
                        </div>
                        <div className="recommendation-body">
                          <div className="recommendation-title">
                            <strong>{item.productId}</strong>
                            <span className="score-badge">
                              匹配 {formatScore(item.finalScore)} 分
                            </span>
                          </div>
                          <p className="price">
                            <small>¥</small>
                            {item.finalPrice}
                          </p>
                          <p className="sku-line">{item.skuId}</p>
                          <div className="reason-heading">
                            <span className="reason-spark">✦</span>
                            <strong>AI 推荐理由</strong>
                          </div>
                          <ul className="reason-list">
                            {item.reasons.map((reason) => (
                              <li key={`${item.skuId}-${reason.text}`}>
                                <span className="reason-check">✓</span>
                                <span>{reason.text}</span>
                              </li>
                            ))}
                          </ul>
                          <button
                            className="product-action"
                            type="button"
                            onClick={() =>
                              void bridge
                                .openProduct({
                                  productId: item.productId,
                                  skuId: item.skuId,
                                })
                                .catch((error: unknown) =>
                                  setMessage(
                                    error instanceof Error
                                      ? error.message
                                      : "商品打开失败",
                                  ),
                                )
                            }
                          >
                            查看商品
                          </button>
                        </div>
                      </li>
                    ))}
                  </ol>
                  {report.report.overallDataGaps.length ? (
                    <p className="data-gap">
                      提示：{report.report.overallDataGaps.join("；")}
                    </p>
                  ) : null}
                  <details className="weights-panel">
                    <summary>结果不太合适？调整关注重点</summary>
                    <div className="weight-controls">
                      {Object.entries(weights).map(([dimension, value]) => (
                        <label className="weight-row" key={dimension}>
                          <span className="weight-label">
                            {weightLabels[dimension] ?? dimension}
                          </span>
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
                                error instanceof Error
                                  ? error.message
                                  : "调权失败",
                              ),
                            )
                            .finally(() => setReweighting(false));
                        }}
                      >
                        {reweighting ? "正在重排…" : "按新偏好重新推荐"}
                      </button>
                    </div>
                  </details>
                </section>
              </div>
            </div>
          ) : null}
        </div>

        <form
          className="chat-composer"
          onSubmit={(event) => {
            event.preventDefault();
            if (!draft.trim()) {
              setMessage("请先描述你的购买目标。");
              return;
            }
            sendContent(draft);
          }}
        >
          <label className="sr-only" htmlFor="decision-input">
            描述你的购买目标
          </label>
          <textarea
            id="decision-input"
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            disabled={snapshot?.status === "WAITING_CLARIFICATION"}
            placeholder={snapshot?.status === "WAITING_CLARIFICATION" ? "请点击上方选项回复" : "说说你想买什么…"}
            rows={1}
          />
          <button aria-label="发送" disabled={submitting || snapshot?.status === "WAITING_CLARIFICATION"} type="submit">
            {submitting ? "···" : "↑"}
          </button>
        </form>
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
  if (path.split("?")[0] === "/admin/decision-runs") return <TraceListPage />;
  const traceRunId = parseTraceRunId(path);
  if (traceRunId)
    return <TracePage runId={traceRunId} initialTrace={initialTrace} />;
  return parsePreview(path) ? (
    <ProductPreview path={path} />
  ) : (
    <Home onPathChange={setPath} />
  );
}

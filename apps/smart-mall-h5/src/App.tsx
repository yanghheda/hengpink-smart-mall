import "./styles.css";

export const smartMallContent = {
  eyebrow: "HENGPICK SMART MALL",
  title: "AI 购物决策体验已就绪",
  description:
    "P01-S01 仅提供独立 H5 启动页；对话、报告与 HostBridge 将按后续阶段接入。",
  badge: "模拟数据 · 最小应用",
} as const;

export function App() {
  return (
    <main className="shell">
      <section className="panel">
        <p className="eyebrow">{smartMallContent.eyebrow}</p>
        <h1>{smartMallContent.title}</h1>
        <p className="description">{smartMallContent.description}</p>
        <p className="badge">{smartMallContent.badge}</p>
      </section>
    </main>
  );
}

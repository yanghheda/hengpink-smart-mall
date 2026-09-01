export type DecisionTraceStep = {
  sequence: number;
  node: string;
  status: string;
  startedAt: string;
  completedAt?: string;
  durationMs: number;
  errorCode?: string;
  warningCodes: string[];
  inputSummary: Record<string, unknown>;
  outputSummary: Record<string, unknown>;
};

export type DecisionTrace = {
  runId: string;
  sessionId: string;
  runVersion: number;
  status: string;
  activeNode?: string;
  failureCode?: string;
  degradationCodes: string[];
  traceId?: string;
  startedAt: string;
  completedAt?: string;
  versions: Record<string, string | undefined>;
  usage: {
    tokenInput?: number;
    tokenOutput?: number;
    estimatedCost?: string;
  };
  steps: DecisionTraceStep[];
};

export type DecisionTraceListItem = {
  runId: string;
  sessionId: string;
  runVersion: number;
  status: string;
  activeNode?: string;
  failureCode?: string;
  startedAt: string;
  completedAt?: string;
};

let currentAccessToken: string | undefined;
const ADMIN_TOKEN_KEY = "hengpick.admin.access-token";

export function rememberH5AccessToken(accessToken: string) {
  currentAccessToken = accessToken;
}

export function readH5AccessToken() {
  return currentAccessToken;
}

export function rememberAdminAccessToken(accessToken: string) {
  if (typeof sessionStorage !== "undefined") sessionStorage.setItem(ADMIN_TOKEN_KEY, accessToken);
}

export function readAdminAccessToken() {
  return typeof sessionStorage === "undefined"
    ? undefined
    : sessionStorage.getItem(ADMIN_TOKEN_KEY) ?? undefined;
}

export function clearAdminAccessToken() {
  if (typeof sessionStorage !== "undefined") sessionStorage.removeItem(ADMIN_TOKEN_KEY);
}

export async function loginDemoAdmin(account: string, password: string) {
  const response = await fetch(
    `${import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080"}/api/v1/auth/login`,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        account,
        password,
        deviceSessionId: `trace-admin-${crypto.randomUUID()}`,
      }),
    },
  );
  const payload = await response.json() as { data?: { accessToken: string }; error?: { message?: string } };
  if (!response.ok || !payload.data) throw new Error(payload.error?.message ?? "管理员登录失败");
  rememberAdminAccessToken(payload.data.accessToken);
  return payload.data.accessToken;
}

type ApiTrace = Omit<DecisionTrace, "versions" | "usage"> & {
  modelVersion?: string;
  promptVersion?: string;
  datasetVersion?: string;
  scoringVersion?: string;
  pricingRuleVersion?: string;
  embeddingVersion?: string;
  tokenInput?: number;
  tokenOutput?: number;
  estimatedCost?: number | string;
};

export async function loadDecisionTrace(runId: string, accessToken: string) {
  const response = await fetch(
    `${import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080"}/api/v1/admin/decision-runs/${encodeURIComponent(runId)}/trace`,
    { headers: { Authorization: `Bearer ${accessToken}` } },
  );
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.error?.message ?? "Trace 加载失败");
  const value = payload.data as ApiTrace;
  return {
    ...value,
    versions: {
      model: value.modelVersion,
      prompt: value.promptVersion,
      dataset: value.datasetVersion,
      scoring: value.scoringVersion,
      pricing: value.pricingRuleVersion,
      embedding: value.embeddingVersion,
    },
    usage: {
      tokenInput: value.tokenInput,
      tokenOutput: value.tokenOutput,
      estimatedCost:
        value.estimatedCost === undefined
          ? undefined
          : String(value.estimatedCost),
    },
  } satisfies DecisionTrace;
}

export async function loadDecisionTraceList(accessToken: string) {
  const response = await fetch(
    `${import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080"}/api/v1/admin/decision-runs?limit=50`,
    { headers: { Authorization: `Bearer ${accessToken}` } },
  );
  const payload = await response.json() as { data?: DecisionTraceListItem[]; error?: { message?: string } };
  if (!response.ok || !payload.data) throw new Error(payload.error?.message ?? "Trace 列表加载失败");
  return payload.data;
}

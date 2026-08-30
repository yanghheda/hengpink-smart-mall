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

let currentAccessToken: string | undefined;

export function rememberH5AccessToken(accessToken: string) {
  currentAccessToken = accessToken;
}

export function readH5AccessToken() {
  return currentAccessToken;
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

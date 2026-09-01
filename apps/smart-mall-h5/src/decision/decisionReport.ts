export type ReportReason = {
  text: string;
  factIds: string[];
  evidenceIds: string[];
};

export type ReportRecommendation = {
  rank: number;
  productId: string;
  skuId: string;
  finalScore: string | number;
  pricePlanId: string;
  finalPrice: string;
  simulated: boolean;
  reasons: ReportReason[];
};

export type DecisionReport = {
  sessionId: string;
  version: number;
  selectedSkuId: string;
  report: {
    summary: string;
    recommendations: ReportRecommendation[];
    overallDataGaps: string[];
    generationType: string;
  };
  versions: Record<string, unknown>;
  createdAt: string;
};

const apiBaseUrl = () =>
  import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080";

async function requireData<T>(response: Response, fallback: string) {
  const payload = (await response.json()) as {
    data?: T;
    error?: { message?: string };
  };
  if (!response.ok || !payload.data) {
    throw new Error(payload.error?.message ?? fallback);
  }
  return payload.data;
}

export async function loadDecisionReport(
  sessionId: string,
  version: number,
  accessToken: string,
  fetcher: typeof fetch = fetch,
) {
  return requireData<DecisionReport>(
    await fetcher(
      `${apiBaseUrl()}/api/v1/decision-sessions/${encodeURIComponent(sessionId)}/reports/${version}`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    ),
    "报告读取失败",
  );
}

export async function reweightDecisionReport(
  sessionId: string,
  reportVersion: number,
  weights: Record<string, number>,
  accessToken: string,
  fetcher: typeof fetch = fetch,
) {
  return requireData<{ version: number }>(
    await fetcher(
      `${apiBaseUrl()}/api/v1/decision-sessions/${encodeURIComponent(sessionId)}/weights`,
      {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "content-type": "application/json",
        },
        body: JSON.stringify({ reportVersion, weights }),
      },
    ),
    "调权失败",
  );
}

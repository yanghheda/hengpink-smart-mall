export type DecisionStreamEvent = {
  eventId: string;
  eventType: string;
  progress: number;
  payload: Record<string, unknown>;
};

export type DecisionSessionSnapshot = {
  sessionId: string;
  currentRunId: string | null;
  currentRunVersion: number;
  status:
    | "DRAFT"
    | "RUNNING"
    | "WAITING_CLARIFICATION"
    | "COMPLETED"
    | "PARTIAL"
    | "FAILED"
    | "SUPERSEDED"
    | "CANCELLED";
  currentReportVersion: number | null;
};

export type DecisionTransportState =
  "STREAMING" | "RECONNECTING" | "POLLING" | "STOPPED";

type Options = {
  url: string;
  accessToken?: string;
  lastEventId?: string;
  fetcher?: typeof fetch;
  signal?: AbortSignal;
  onEvent: (event: DecisionStreamEvent) => void;
};

function compareRedisIds(left: string, right: string) {
  const [leftMs, leftSequence] = left.split("-").map(BigInt);
  const [rightMs, rightSequence] = right.split("-").map(BigInt);
  if (leftMs !== rightMs) return leftMs < rightMs ? -1 : 1;
  if (leftSequence === rightSequence) return 0;
  return leftSequence < rightSequence ? -1 : 1;
}

function parseBlock(block: string): DecisionStreamEvent | undefined {
  let eventId = "";
  let eventType = "message";
  const data: string[] = [];
  for (const line of block.split("\n")) {
    if (line.startsWith("id:")) eventId = line.slice(3).trim();
    if (line.startsWith("event:")) eventType = line.slice(6).trim();
    if (line.startsWith("data:")) data.push(line.slice(5).trimStart());
  }
  if (!eventId || data.length === 0) return undefined;
  const payload = JSON.parse(data.join("\n")) as Record<string, unknown>;
  return {
    eventId,
    eventType,
    progress: typeof payload.progress === "number" ? payload.progress : 0,
    payload,
  };
}

export async function consumeDecisionStream({
  url,
  accessToken,
  lastEventId,
  fetcher = fetch,
  signal,
  onEvent,
}: Options) {
  const headers: Record<string, string> = { Accept: "text/event-stream" };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (lastEventId) headers["Last-Event-ID"] = lastEventId;
  const response = await fetcher(url, { headers, signal });
  if (!response.ok || !response.body)
    throw new Error(`SSE 连接失败: ${response.status}`);

  let cursor = lastEventId;
  let progress = 0;
  let buffer = "";
  const decoder = new TextDecoder();
  for await (const chunk of response.body) {
    buffer += decoder.decode(chunk, { stream: true }).replaceAll("\r\n", "\n");
    let boundary = buffer.indexOf("\n\n");
    while (boundary >= 0) {
      const event = parseBlock(buffer.slice(0, boundary));
      buffer = buffer.slice(boundary + 2);
      if (
        event &&
        (!cursor || compareRedisIds(event.eventId, cursor) > 0) &&
        event.progress >= progress
      ) {
        cursor = event.eventId;
        progress = event.progress;
        onEvent(event);
      }
      boundary = buffer.indexOf("\n\n");
    }
  }
  return cursor;
}

const terminalStatuses = new Set<DecisionSessionSnapshot["status"]>([
  "WAITING_CLARIFICATION",
  "COMPLETED",
  "PARTIAL",
  "FAILED",
  "SUPERSEDED",
  "CANCELLED",
]);

export async function fetchDecisionSessionSnapshot({
  url,
  accessToken,
  fetcher = fetch,
  signal,
}: {
  url: string;
  accessToken?: string;
  fetcher?: typeof fetch;
  signal?: AbortSignal;
}) {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const response = await fetcher(url, { headers, signal });
  if (!response.ok) throw new Error(`Session 快照读取失败: ${response.status}`);
  const envelope = (await response.json()) as { data: DecisionSessionSnapshot };
  return envelope.data;
}

export async function recoverDecisionSession({
  fetchSnapshot,
  consumeStream,
  onSnapshot,
  onTransportState,
  wait = (milliseconds) =>
    new Promise<void>((resolve) =>
      globalThis.setTimeout(resolve, milliseconds),
    ),
  pollIntervalMs = 2_000,
  maxSseFailures = 3,
}: {
  fetchSnapshot: () => Promise<DecisionSessionSnapshot>;
  consumeStream: () => Promise<unknown>;
  onSnapshot: (snapshot: DecisionSessionSnapshot) => void;
  onTransportState: (state: DecisionTransportState) => void;
  wait?: (milliseconds: number) => Promise<void>;
  pollIntervalMs?: number;
  maxSseFailures?: number;
}) {
  let snapshot = await fetchSnapshot();
  onSnapshot(snapshot);
  if (terminalStatuses.has(snapshot.status)) {
    onTransportState("STOPPED");
    return snapshot;
  }

  for (let failures = 0; failures < maxSseFailures; failures += 1) {
    onTransportState(failures === 0 ? "STREAMING" : "RECONNECTING");
    try {
      await consumeStream();
    } catch {
      // 连接失败由状态切换显式呈现，达到阈值后由 MySQL 轮询接管。
    }
  }

  onTransportState("POLLING");
  while (true) {
    await wait(pollIntervalMs);
    snapshot = await fetchSnapshot();
    onSnapshot(snapshot);
    if (terminalStatuses.has(snapshot.status)) {
      onTransportState("STOPPED");
      return snapshot;
    }
  }
}

export type DecisionStreamEvent = {
  eventId: string;
  eventType: string;
  progress: number;
  payload: Record<string, unknown>;
};

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

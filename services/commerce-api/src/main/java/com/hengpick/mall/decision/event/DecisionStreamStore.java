package com.hengpick.mall.decision.event;

import java.util.List;

/** 保存和续读短期进度事件，最终业务状态不由此端口负责。 */
public interface DecisionStreamStore {
    boolean append(DecisionStreamEvent event, String dedupeKey);

    String latestId(String runId);

    List<DecisionStreamEvent> readAfter(String runId, String lastEventId, int count);
}

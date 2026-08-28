package com.hengpick.mall.decision.application;

import com.hengpick.mall.decision.domain.DecisionRun;
import com.hengpick.mall.decision.domain.DecisionSession;

/** 原子创建新 Run 后返回的会话与 Run 快照。 */
public record StartedDecisionRun(DecisionSession session, DecisionRun run) {}

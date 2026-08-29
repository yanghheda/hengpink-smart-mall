package com.hengpick.mall.integration.agent;

/** 向 Agent Service 提交 Run 的同步传输端口，由外层异步执行器调用。 */
@FunctionalInterface
public interface AgentRunClient {
    void start(AgentRunRequest request);
}

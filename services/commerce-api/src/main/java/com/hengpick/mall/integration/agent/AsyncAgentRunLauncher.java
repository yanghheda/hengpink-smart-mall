package com.hengpick.mall.integration.agent;

import java.util.Objects;
import java.util.concurrent.Executor;

/** 使用受控执行器异步提交 Agent Run，不占用创建请求线程。 */
public final class AsyncAgentRunLauncher {
    private final Executor executor;
    private final AgentRunClient client;

    public AsyncAgentRunLauncher(Executor executor, AgentRunClient client) {
        this.executor = Objects.requireNonNull(executor);
        this.client = Objects.requireNonNull(client);
    }

    public void launch(AgentRunRequest request) {
        executor.execute(() -> client.start(request));
    }
}

package com.hengpick.mall.integration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AsyncAgentRunLauncherTest {
    @Test
    void dispatchesAgentRequestThroughExecutorWithoutChangingPayload() {
        var submitted = new AtomicReference<Runnable>();
        Executor executor = submitted::set;
        var received = new AtomicReference<AgentRunRequest>();
        var launcher = new AsyncAgentRunLauncher(executor, received::set);
        var request = AgentRunRequest.stub("RUN-1", "SESSION-1", 1, "dataset-v1", "token");

        launcher.launch(request);

        assertThat(received).hasValue(null);
        submitted.get().run();
        assertThat(received).hasValue(request);
    }
}

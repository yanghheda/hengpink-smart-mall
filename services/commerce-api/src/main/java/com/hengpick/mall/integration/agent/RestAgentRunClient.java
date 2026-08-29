package com.hengpick.mall.integration.agent;

import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

/** 通过内部 HTTP 接口向 Agent Service 提交 Run，并要求返回 202。 */
public final class RestAgentRunClient implements AgentRunClient {
    private final RestClient client;

    public RestAgentRunClient(URI agentBaseUrl) {
        client = RestClient.builder().baseUrl(agentBaseUrl.toString()).build();
    }

    @Override
    public void start(AgentRunRequest request) {
        var response = client.post()
                .uri("/internal/v1/agent-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        if (response.getStatusCode() != HttpStatus.ACCEPTED) {
            throw new IllegalStateException("Agent Service 未按协议返回 202");
        }
    }
}

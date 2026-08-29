package com.hengpick.mall.integration.agent.tool;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Agent Service 唯一可调用的固定 Commerce Tool Registry。 */
@RestController
@Profile("database")
@RequestMapping("/internal/v1/tools")
public class CommerceToolController {
    private final CommerceToolService service;
    private final String serviceToken;

    public CommerceToolController(
            CommerceToolService service,
            @Value("${hengpick.commerce.internal-service-token}") String serviceToken) {
        this.service = service;
        this.serviceToken = serviceToken;
    }

    @PostMapping("/{toolName:search-products|get-product-specs|get-price-offers|calculate-final-price|score-candidates}")
    ResponseEntity<ToolResponseEnvelope> execute(
            @PathVariable String toolName,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ToolRequestEnvelope request) {
        if (!java.security.MessageDigest.isEqual(
                ("Bearer " + serviceToken).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                String.valueOf(authorization).getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部服务凭证无效");
        }
        return ResponseEntity.ok(service.execute(toolName, request));
    }
}

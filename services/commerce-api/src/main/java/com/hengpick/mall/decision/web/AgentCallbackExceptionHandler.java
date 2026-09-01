package com.hengpick.mall.decision.web;

import com.hengpick.mall.decision.application.CallbackConflictException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将内部回调认证和幂等冲突映射为稳定协议错误。 */
@RestControllerAdvice(assignableTypes = AgentCallbackController.class)
public class AgentCallbackExceptionHandler {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(AgentCallbackExceptionHandler.class);
    @ExceptionHandler(InvalidCallbackTokenException.class)
    ResponseEntity<Map<String, Object>> invalidToken() {
        return error(HttpStatus.UNAUTHORIZED, "AGENT_CALLBACK_UNAUTHORIZED", "回调凭证无效或已过期");
    }

    @ExceptionHandler(CallbackConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(CallbackConflictException exception) {
        LOGGER.warn("Agent 回调冲突: {}", exception.getMessage());
        return error(HttpStatus.CONFLICT, "AGENT_CALLBACK_CONFLICT", exception.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message, "retryable", false)));
    }
}

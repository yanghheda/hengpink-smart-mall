package com.hengpick.mall.decision.web;

import com.hengpick.mall.catalog.web.CatalogExceptionHandler.ApiError;
import com.hengpick.mall.catalog.web.CatalogExceptionHandler.ErrorEnvelope;
import com.hengpick.mall.decision.application.DecisionStreamNotFoundException;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 把订阅鉴权错误映射为不泄露内部信息的稳定响应。 */
@RestControllerAdvice(assignableTypes = DecisionStreamController.class)
public class DecisionStreamExceptionHandler {
    @ExceptionHandler(JwtException.class)
    ResponseEntity<ErrorEnvelope> unauthorized(JwtException exception) {
        return error(HttpStatus.UNAUTHORIZED, "H5_SESSION_INVALID", "H5 会话无效或已过期");
    }

    @ExceptionHandler(DecisionStreamNotFoundException.class)
    ResponseEntity<ErrorEnvelope> notFound(DecisionStreamNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "DECISION_STREAM_NOT_FOUND", exception.getMessage());
    }

    private ResponseEntity<ErrorEnvelope> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(UUID.randomUUID().toString(),
                new ApiError(code, message, false, List.of())));
    }
}

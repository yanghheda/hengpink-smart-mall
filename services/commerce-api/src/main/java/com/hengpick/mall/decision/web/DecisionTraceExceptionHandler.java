package com.hengpick.mall.decision.web;

import com.hengpick.mall.catalog.web.CatalogExceptionHandler.ApiError;
import com.hengpick.mall.catalog.web.CatalogExceptionHandler.ErrorEnvelope;
import com.hengpick.mall.decision.application.DecisionTraceNotFoundException;
import com.hengpick.mall.identity.application.ObjectAccessDeniedException;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将 Trace 鉴权与资源错误转换为稳定的公开错误码。 */
@RestControllerAdvice(assignableTypes = DecisionTraceController.class)
public class DecisionTraceExceptionHandler {
    @ExceptionHandler(JwtException.class)
    ResponseEntity<ErrorEnvelope> unauthorized(JwtException exception) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "H5 Access Token 无效");
    }

    @ExceptionHandler(ObjectAccessDeniedException.class)
    ResponseEntity<ErrorEnvelope> forbidden(ObjectAccessDeniedException exception) {
        return error(HttpStatus.FORBIDDEN, exception.code(), exception.getMessage());
    }

    @ExceptionHandler(DecisionTraceNotFoundException.class)
    ResponseEntity<ErrorEnvelope> notFound(DecisionTraceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "DECISION_TRACE_NOT_FOUND", exception.getMessage());
    }

    private ResponseEntity<ErrorEnvelope> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(UUID.randomUUID().toString(),
                new ApiError(code, message, false, List.of())));
    }
}

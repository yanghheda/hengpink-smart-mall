package com.hengpick.mall.decision.web;

import com.hengpick.mall.catalog.web.CatalogExceptionHandler.ApiError;
import com.hengpick.mall.catalog.web.CatalogExceptionHandler.ErrorEnvelope;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将首次分析入口的认证与参数错误映射为稳定公开响应。 */
@RestControllerAdvice(assignableTypes = DecisionSubmissionController.class)
public class DecisionSubmissionExceptionHandler {
    @ExceptionHandler(JwtException.class)
    ResponseEntity<ErrorEnvelope> unauthorized(JwtException exception) {
        return error(HttpStatus.UNAUTHORIZED, "H5_SESSION_INVALID", "H5 会话无效或已过期");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorEnvelope> invalid(MethodArgumentNotValidException exception) {
        return error(HttpStatus.BAD_REQUEST, "DECISION_REQUIREMENT_INVALID", "请提供有效的购买需求");
    }

    private ResponseEntity<ErrorEnvelope> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(UUID.randomUUID().toString(),
                new ApiError(code, message, false, List.of())));
    }
}

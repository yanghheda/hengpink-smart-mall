package com.hengpick.mall.engagement.web;

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

@RestControllerAdvice(assignableTypes = EngagementController.class)
public class EngagementExceptionHandler {
    @ExceptionHandler(JwtException.class)
    ResponseEntity<ErrorEnvelope> unauthorized(JwtException exception) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "H5 Access Token 无效");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ErrorEnvelope> invalidRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "ENGAGEMENT_RESOURCE_NOT_FOUND", "收藏或历史报告不存在");
    }

    private ResponseEntity<ErrorEnvelope> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(UUID.randomUUID().toString(),
                new ApiError(code, message, false, List.of())));
    }
}

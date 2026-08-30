package com.hengpick.mall.memory.web;

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

@RestControllerAdvice(assignableTypes = MemoryController.class)
public class MemoryExceptionHandler {
    @ExceptionHandler(JwtException.class)
    ResponseEntity<ErrorEnvelope> unauthorized(JwtException exception) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "H5 Access Token 无效");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ErrorEnvelope> invalidRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "MEMORY_REQUEST_INVALID", "Memory 请求参数或资源归属不合法");
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorEnvelope> conflict(IllegalStateException exception) {
        return error(HttpStatus.CONFLICT, "MEMORY_PROPOSAL_CONFLICT", exception.getMessage());
    }

    private ResponseEntity<ErrorEnvelope> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(UUID.randomUUID().toString(),
                new ApiError(code, message, false, List.of())));
    }
}

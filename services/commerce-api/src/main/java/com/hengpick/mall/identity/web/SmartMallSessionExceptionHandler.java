package com.hengpick.mall.identity.web;

import com.hengpick.mall.catalog.web.CatalogExceptionHandler.ApiError;
import com.hengpick.mall.catalog.web.CatalogExceptionHandler.ErrorEnvelope;
import com.hengpick.mall.identity.application.SmartMallTicketException;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SmartMallSessionController.class)
public class SmartMallSessionExceptionHandler {
    @ExceptionHandler(SmartMallTicketException.class)
    ResponseEntity<ErrorEnvelope> ticketFailed(SmartMallTicketException exception) {
        var retryable = "SMART_TICKET_EXPIRED".equals(exception.code());
        return error(HttpStatus.valueOf("BRIDGE_VERSION_UNSUPPORTED".equals(exception.code()) ? 400 : 401),
                exception.code(), exception.getMessage(), retryable);
    }

    @ExceptionHandler(JwtException.class)
    ResponseEntity<ErrorEnvelope> accessTokenFailed(JwtException exception) {
        return error(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "RN Access Token 无效或已过期", false);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorEnvelope> invalidRequest(MethodArgumentNotValidException exception) {
        return error(HttpStatus.BAD_REQUEST, "SMART_MALL_REQUEST_INVALID", "Smart Mall 请求参数不合法", false);
    }

    private ResponseEntity<ErrorEnvelope> error(HttpStatus status, String code, String message, boolean retryable) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(UUID.randomUUID().toString(),
                new ApiError(code, message, retryable, List.of())));
    }
}

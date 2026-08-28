package com.hengpick.mall.identity.web;

import com.hengpick.mall.catalog.web.CatalogExceptionHandler.ApiError;
import com.hengpick.mall.catalog.web.CatalogExceptionHandler.ErrorEnvelope;
import com.hengpick.mall.identity.application.AuthenticationFailedException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {
    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ErrorEnvelope> authenticationFailed(AuthenticationFailedException exception) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorEnvelope> invalidRequest(MethodArgumentNotValidException exception) {
        return error(HttpStatus.BAD_REQUEST, "AUTH_REQUEST_INVALID", "登录请求参数不合法");
    }

    private ResponseEntity<ErrorEnvelope> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(UUID.randomUUID().toString(),
                new ApiError(code, message, false, List.of())));
    }
}

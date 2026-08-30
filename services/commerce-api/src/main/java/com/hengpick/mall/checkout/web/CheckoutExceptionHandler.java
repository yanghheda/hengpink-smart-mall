package com.hengpick.mall.checkout.web;

import com.hengpick.mall.catalog.web.CatalogExceptionHandler.ApiError;
import com.hengpick.mall.catalog.web.CatalogExceptionHandler.ErrorEnvelope;
import com.hengpick.mall.checkout.application.PricePlanStaleException;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CheckoutController.class)
public class CheckoutExceptionHandler {
    @ExceptionHandler(PricePlanStaleException.class)
    ResponseEntity<ErrorEnvelope> stale() {
        return error(HttpStatus.CONFLICT, "PRICE_PLAN_STALE", "价格方案已变化，请刷新报告后重试");
    }
    @ExceptionHandler(JwtException.class)
    ResponseEntity<ErrorEnvelope> unauthorized() {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "访问凭证无效");
    }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ErrorEnvelope> invalid(RuntimeException exception) {
        return error(HttpStatus.BAD_REQUEST, "PURCHASE_INTENT_INVALID", exception.getMessage());
    }
    private ResponseEntity<ErrorEnvelope> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(UUID.randomUUID().toString(),
                new ApiError(code, message, false, List.of())));
    }
}

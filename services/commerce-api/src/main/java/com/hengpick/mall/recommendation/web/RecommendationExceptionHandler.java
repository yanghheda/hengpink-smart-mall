package com.hengpick.mall.recommendation.web;

import com.hengpick.mall.catalog.web.CatalogExceptionHandler.ApiError;
import com.hengpick.mall.catalog.web.CatalogExceptionHandler.ErrorEnvelope;
import com.hengpick.mall.recommendation.domain.ReportVersionConflictException;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RecommendationController.class)
public class RecommendationExceptionHandler {
    @ExceptionHandler(JwtException.class)
    ResponseEntity<ErrorEnvelope> unauthorized(JwtException exception) {
        return error(HttpStatus.UNAUTHORIZED, "H5_SESSION_INVALID", "H5 会话无效或已过期");
    }

    @ExceptionHandler(ReportVersionConflictException.class)
    ResponseEntity<ErrorEnvelope> conflict(ReportVersionConflictException exception) {
        return error(HttpStatus.CONFLICT, "REPORT_VERSION_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ErrorEnvelope> invalid(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "REWEIGHT_REQUEST_INVALID", exception.getMessage());
    }

    private ResponseEntity<ErrorEnvelope> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(UUID.randomUUID().toString(),
                new ApiError(code, message, false, List.of())));
    }
}

package com.hengpick.mall.catalog.web;

import com.hengpick.mall.catalog.application.ProductNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.context.annotation.Profile;

@RestControllerAdvice(assignableTypes = CatalogController.class)
@Profile("database")
public class CatalogExceptionHandler {
    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ErrorEnvelope> notFound(ProductNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorEnvelope> invalidQuery(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "CATALOG_QUERY_INVALID", "商品查询参数不合法");
    }

    private ResponseEntity<ErrorEnvelope> error(HttpStatus status, String code, String message) {
        var body = new ErrorEnvelope(UUID.randomUUID().toString(), new ApiError(code, message, false, List.of()));
        return ResponseEntity.status(status).body(body);
    }

    /** 失败响应的统一信封。 */
    public record ErrorEnvelope(
            /*
             * 服务端生成的请求标识。
             */
            String requestId,
            /*
             * 机器可读的错误信息。
             */
            ApiError error) {}

    /** 失败响应中的错误详情。 */
    public record ApiError(
            /*
             * 稳定的机器可读错误代码。
             */
            String code,
            /*
             * 面向调用方的安全错误说明。
             */
            String message,
            /*
             * 调用方是否可在不改变请求的前提下重试。
             */
            boolean retryable,
            /*
             * 可选的结构化错误明细。
             */
            List<Object> details) {}
}

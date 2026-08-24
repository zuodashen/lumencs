package com.lumencs.common;

import com.lumencs.exception.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> biz(BizException ex) {
        return ResponseEntity.status(ex.getCode() >= 500 ? 500 : 400).body(R.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<R<Void>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(R.fail(400, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> invalid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("参数不合法");
        return ResponseEntity.badRequest().body(R.fail(400, msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> fallback(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(500, ex.getMessage() == null ? "内部错误" : ex.getMessage()));
    }
}

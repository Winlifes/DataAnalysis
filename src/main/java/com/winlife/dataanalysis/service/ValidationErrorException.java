// src/main/java/com/winlife/dataanalysis/service/ValidationErrorException.java
package com.winlife.dataanalysis.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// 继承 RuntimeException，表示这是一个非检查异常
// @ResponseStatus(HttpStatus.BAD_REQUEST) // 可选：如果希望 Spring MVC 自动将此异常映射到 400 状态码
public class ValidationErrorException extends RuntimeException {
    public ValidationErrorException(String message) {
        super(message);
    }

    public ValidationErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
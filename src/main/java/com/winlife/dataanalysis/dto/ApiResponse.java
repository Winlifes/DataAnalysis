// src/main/java/com/winlife/dataanalysis/dto/ApiResponse.java
package com.winlife.dataanalysis.dto;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private boolean success; // 操作是否成功
    private String code; // 业务状态码 (例如: "200" 表示成功, "VALIDATION_ERROR" 表示验证失败, "INTERNAL_ERROR" 表示内部错误)
    private String message; // 提示信息
    private T data; // 返回的数据载荷 (可选)

    // 私有构造函数，强制使用静态工厂方法创建实例
    private ApiResponse() {}

    // --- 静态工厂方法 ---

    // 成功响应，带数据
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.code = "200";
        response.message = "Operation successful";
        response.data = data;
        return response;
    }

    // 成功响应，带消息和数据
    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.code = "200";
        response.message = message;
        response.data = data;
        return response;
    }

    // 成功响应，带自定义 code, 消息和数据
    public static <T> ApiResponse<T> success(String code, String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.code = code;
        response.message = message;
        response.data = data;
        return response;
    }


    // 成功响应，不带数据
    public static <T> ApiResponse<T> success() {
        return success("Operation successful", null);
    }

    // 成功响应，带消息但不带数据
    public static <T> ApiResponse<T> success(String message) {
        return success(message, null);
    }


    // 错误响应，带 code 和消息
    public static <T> ApiResponse<T> error(String code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.code = code;
        response.message = message;
        return response;
    }

    // 错误响应，带 code, 消息和数据 (例如，返回错误详情)
    public static <T> ApiResponse<T> error(String code, String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.code = code;
        response.message = message;
        response.data = data;
        return response;
    }
}
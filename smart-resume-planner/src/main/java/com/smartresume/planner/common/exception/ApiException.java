package com.smartresume.planner.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base type for every error we want the client to see with a stable machine-readable code. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message);
    }

    public static ApiException unavailable(String code, String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}

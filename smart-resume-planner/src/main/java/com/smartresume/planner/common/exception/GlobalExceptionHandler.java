package com.smartresume.planner.common.exception;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One place that turns every failure into an RFC 7807 problem body:
 * {@code {type, title, status, detail, code, timestamp, errors?}}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("API error {}: {}", ex.getCode(), ex.getMessage());
        } else {
            log.debug("API error {}: {}", ex.getCode(), ex.getMessage());
        }
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraint(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The record was changed by someone else. Reload and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "DATA_CONFLICT", "The request conflicts with existing data");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong on our side");
    }

    // ---- Spring MVC's own exceptions (bad JSON, missing params, wrong method...) ----

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                problem(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File exceeds the 10 MB upload limit"));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(org.springframework.beans.TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        return ResponseEntity.badRequest().body(
                problem(HttpStatus.BAD_REQUEST, "BAD_PARAMETER", "Invalid value for parameter '" + name + "'"));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed"));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing"));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "MISSING_PART",
                "Required part '" + ex.getRequestPartName() + "' is missing"));
    }

    static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setType(URI.create("urn:smart-resume-planner:error:" + code.toLowerCase().replace('_', '-')));
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}

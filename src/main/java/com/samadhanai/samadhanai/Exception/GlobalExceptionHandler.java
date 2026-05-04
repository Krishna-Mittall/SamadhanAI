package com.samadhanai.samadhanai.Exception;

import com.samadhanai.samadhanai.Common.Response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ─── Complaint Not Found ──────────────────────────────
    @ExceptionHandler(AppExceptions.ComplaintNotFoundException.class)
    public ResponseEntity<ApiResponse> handleNotFound(AppExceptions.ComplaintNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse.builder()
                        .success(false).message(ex.getMessage())
                        .timestamp(LocalDateTime.now()).build());
    }

    // ─── Fake Photo ───────────────────────────────────────
    @ExceptionHandler(AppExceptions.FakePhotoException.class)
    public ResponseEntity<ApiResponse> handleFakePhoto(AppExceptions.FakePhotoException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse.builder()
                        .success(false).message(ex.getMessage())
                        .timestamp(LocalDateTime.now()).build());
    }

    // ─── Photo Storage ────────────────────────────────────
    @ExceptionHandler(AppExceptions.PhotoStorageException.class)
    public ResponseEntity<ApiResponse> handlePhotoStorage(AppExceptions.PhotoStorageException ex) {
        log.error("Photo storage error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.builder()
                        .success(false).message("Photo storage failed: " + ex.getMessage())
                        .timestamp(LocalDateTime.now()).build());
    }

    // ─── Email Send ───────────────────────────────────────
    @ExceptionHandler(AppExceptions.EmailSendException.class)
    public ResponseEntity<ApiResponse> handleEmail(AppExceptions.EmailSendException ex) {
        log.error("Email error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.builder()
                        .success(false).message("Email failed: " + ex.getMessage())
                        .timestamp(LocalDateTime.now()).build());
    }

    // ─── Validation Errors ────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field   = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse.builder()
                        .success(false).message("Validation failed")
                        .data(errors)
                        .timestamp(LocalDateTime.now()).build());
    }

    // ─── Illegal Argument ─────────────────────────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse> handleIllegal(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse.builder()
                        .success(false).message(ex.getMessage())
                        .timestamp(LocalDateTime.now()).build());
    }

    // ─── Static Resource Not Found (favicon etc) ────────
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex) {
        // Silently return 404 — no error log needed for missing static files
        return ResponseEntity.notFound().build();
    }

    // ─── Generic Fallback ─────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.builder()
                        .success(false).message("Something went wrong. Please try again.")
                        .timestamp(LocalDateTime.now()).build());
    }
}
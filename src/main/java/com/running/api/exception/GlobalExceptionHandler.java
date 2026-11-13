package com.running.api.exception;

import com.running.api.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponseDto> handleDuplicateEmail(DuplicateEmailException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponseDto> handleForbidden(ForbiddenOperationException ex, HttpServletRequest request) {
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(RecommendationValidationException.class)
    public ResponseEntity<ErrorResponseDto> handleRecommendationValidation(
            RecommendationValidationException ex, HttpServletRequest request) {
        String detail = ex.getViolations().stream()
                .map(v -> v.code() + ": " + v.message())
                .reduce((a, b) -> a + "; " + b)
                .orElse(ex.getMessage());
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, detail, request);
    }

    @ExceptionHandler(RecommendationAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDto> handleRecommendationExists(
            RecommendationAlreadyExistsException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(GenerationJobStateException.class)
    public ResponseEntity<ErrorResponseDto> handleJobState(GenerationJobStateException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(GenerationNotConfiguredException.class)
    public ResponseEntity<ErrorResponseDto> handleGenerationNotConfigured(
            GenerationNotConfiguredException ex, HttpServletRequest request) {
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(GenerationException.class)
    public ResponseEntity<ErrorResponseDto> handleGeneration(GenerationException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    @ExceptionHandler(AnalysisJobStateException.class)
    public ResponseEntity<ErrorResponseDto> handleAnalysisJobState(
            AnalysisJobStateException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(AnalysisNotConfiguredException.class)
    public ResponseEntity<ErrorResponseDto> handleAnalysisNotConfigured(
            AnalysisNotConfiguredException ex, HttpServletRequest request) {
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<ErrorResponseDto> handleAnalysis(AnalysisException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** JSON malformado, parámetro faltante o de tipo incorrecto, validación de body, etc. */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            BindException.class
    })
    public ResponseEntity<ErrorResponseDto> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "Petición incorrecta: " + ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponseDto> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                                        HttpServletRequest request) {
        return buildError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDto> handlePayloadTooLarge(MaxUploadSizeExceededException ex,
                                                                  HttpServletRequest request) {
        return buildError(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el tamaño máximo permitido", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno en el servidor", request);
    }

    private ResponseEntity<ErrorResponseDto> buildError(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponseDto error = ErrorResponseDto.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(error);
    }
}

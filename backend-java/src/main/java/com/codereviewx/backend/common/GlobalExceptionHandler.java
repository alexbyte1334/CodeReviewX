package com.codereviewx.backend.common;

import com.codereviewx.backend.review.exception.ReviewRunNotFoundException;
import com.codereviewx.backend.review.exception.CommentPreviewNotFoundException;
import com.codereviewx.backend.review.exception.ReviewRequestInvalidException;
import com.codereviewx.backend.review.exception.ReviewTaskNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.codereviewx.backend.rag.controller.RagNotFoundException;
import com.codereviewx.backend.rag.controller.RagConflictException;
import com.codereviewx.backend.rag.controller.RagDisabledException;
import com.codereviewx.backend.rag.controller.RagInvalidRequestException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) { return ApiResponse.failure("Invalid request"); }
    @ExceptionHandler(RagNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleRagNotFound(RagNotFoundException ex) { return ApiResponse.failure("Not found"); }
    @ExceptionHandler(RagConflictException.class) @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleRagConflict(RagConflictException ex) { return ApiResponse.failure("Already queued"); }
    @ExceptionHandler(RagDisabledException.class) @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleRagDisabled(RagDisabledException ex) { return ApiResponse.failure("RAG unavailable"); }
    @ExceptionHandler({RagInvalidRequestException.class, ConstraintViolationException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, HandlerMethodValidationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleRagInvalidRequest(Exception ex) { return ApiResponse.failure("Invalid request"); }

    @ExceptionHandler(ReviewTaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleReviewTaskNotFound(ReviewTaskNotFoundException ex) {
        return ApiResponse.failure("Review task not found");
    }

    @ExceptionHandler(ReviewRunNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleReviewRunNotFound(ReviewRunNotFoundException ex) {
        return ApiResponse.failure("Review run not found");
    }

    @ExceptionHandler(CommentPreviewNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleCommentPreviewNotFound(CommentPreviewNotFoundException ex) {
        return ApiResponse.failure("Comment preview not found");
    }

    @ExceptionHandler(ReviewRequestInvalidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleReviewRequestInvalid(ReviewRequestInvalidException ex) {
        return ApiResponse.failure(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ApiResponse.failure("Validation failed: " + message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleGenericException(Exception ex) {
        return ApiResponse.failure("Internal server error");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleMissingResource(NoResourceFoundException ex) {
        return ApiResponse.failure("Not found");
    }
}

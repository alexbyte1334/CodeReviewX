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

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
}

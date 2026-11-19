package com.example.animalpictures.web;

import com.example.animalpictures.source.PictureSourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns errors into RFC 9457 "problem detail" JSON responses. The base class already handles Spring's
 * own errors (e.g. {@code count=abc} becomes 400).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    @ExceptionHandler(PictureNotFoundException.class)
    ProblemDetail handleNotFound(PictureNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Picture not found", e.getMessage());
    }

    @ExceptionHandler(PictureSourceException.class)
    ProblemDetail handlePictureSource(PictureSourceException e) {
        log.warn("Picture API failed: {}", e.getMessage(), e.getCause());
        return problem(HttpStatus.BAD_GATEWAY, "Picture API unavailable",
                e.getMessage() + ". Nothing was stored; please try again.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}

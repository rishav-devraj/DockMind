package com.dockmind.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Every error leaves the API in the contract's shape, including the ones Spring raises itself. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ApiError(String error, String message) {
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApi(ApiException ex) {
        return render(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<Object> handleNotMultipart(MultipartException ex) {
        return render(HttpStatus.BAD_REQUEST, "MISSING_IMAGE", "Send the photo as a multipart file named image.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unhandled API error", ex);
        return render(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong on the server.");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpStatus resolved = HttpStatus.valueOf(status.value());
        if (ex instanceof MissingRequestHeaderException header) {
            return render(resolved, "MISSING_HEADER", "The " + header.getHeaderName() + " header is required.");
        }
        if (ex instanceof MissingServletRequestPartException) {
            return render(resolved, "MISSING_IMAGE", "A photo is required in the file field named image.");
        }
        if (ex instanceof MaxUploadSizeExceededException) {
            return render(resolved, "IMAGE_TOO_LARGE", "The photo is larger than the 10 MB limit.");
        }
        return render(resolved, resolved.name(), genericMessage(resolved));
    }

    private static String genericMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "The request is not valid.";
            case NOT_FOUND -> "There is nothing at that address.";
            case METHOD_NOT_ALLOWED -> "That method is not allowed here.";
            case UNSUPPORTED_MEDIA_TYPE -> "That content type is not supported.";
            default -> "The request could not be completed.";
        };
    }

    private static ResponseEntity<Object> render(HttpStatus status, String code, String message) {
        // The explicit type keeps errors renderable on A9, where the client accepts only images.
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(new ApiError(code, message));
    }
}

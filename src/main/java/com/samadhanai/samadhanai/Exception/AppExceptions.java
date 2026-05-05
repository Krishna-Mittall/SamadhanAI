package com.samadhanai.samadhanai.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * All custom exceptions in one file.
 * Usage: throw new AppExceptions.ComplaintNotFoundException("msg");
 */
public class AppExceptions {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ComplaintNotFoundException extends RuntimeException {
        public ComplaintNotFoundException(String msg) { super(msg); }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class FakePhotoException extends RuntimeException {
        public FakePhotoException(String msg) { super(msg); }
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public static class PhotoStorageException extends RuntimeException {
        public PhotoStorageException(String msg) { super(msg); }
        public PhotoStorageException(String msg, Throwable cause) { super(msg, cause); }
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public static class EmailSendException extends RuntimeException {
        public EmailSendException(String msg) { super(msg); }
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public static class ComplaintSubmissionException extends RuntimeException {
        public ComplaintSubmissionException(String msg) { super(msg); }
        public ComplaintSubmissionException(String msg, Throwable cause) { super(msg, cause); }
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String msg) { super(msg); }
    }
}
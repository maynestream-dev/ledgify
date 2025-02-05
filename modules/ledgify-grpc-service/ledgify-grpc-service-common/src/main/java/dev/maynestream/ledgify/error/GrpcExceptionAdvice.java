package dev.maynestream.ledgify.error;

import build.buf.protovalidate.exceptions.ValidationException;
import build.buf.validate.Violation;
import dev.maynestream.ledgify.validation.ConstraintViolationException;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.springframework.dao.DataIntegrityViolationException;

@Slf4j
@GrpcAdvice
public class GrpcExceptionAdvice {

    @GrpcExceptionHandler(IllegalArgumentException.class)
    public Status handleIllegalArgument(IllegalArgumentException e) {
        return Status.INVALID_ARGUMENT.withDescription("A validation exception occurred: " + e.getMessage()).withCause(e);
    }

    @GrpcExceptionHandler(ConstraintViolationException.class)
    public Status handleConstraintViolation(ConstraintViolationException e) {
        Status status = Status.INVALID_ARGUMENT.withDescription("A validation exception occurred").withCause(e);
        if (!e.getViolations().isEmpty()) {
            status = status.augmentDescription("  Violations:");
            for (Violation v : e.getViolations()) {
                status = status.augmentDescription("    %s: %s".formatted(v.getFieldPath(), v.getMessage()));
            }
        }
        return status;
    }

    @GrpcExceptionHandler(ValidationException.class)
    public Status handleValidation(ValidationException e) {
        return Status.INVALID_ARGUMENT.withDescription("A validation exception occurred: " + e.getMessage()).withCause(e);
    }

    @GrpcExceptionHandler(DataIntegrityViolationException.class)
    public Status handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return Status.INVALID_ARGUMENT.withDescription("A validation exception occurred: " + e.getMostSpecificCause().getMessage()).withCause(e);
    }

    @GrpcExceptionHandler(Throwable.class)
    public Status handleThrowable(Throwable e) {
        log.error("Unhandled exception", e);
        return Status.UNKNOWN.withDescription("Could not handle request").withCause(e);
    }
}
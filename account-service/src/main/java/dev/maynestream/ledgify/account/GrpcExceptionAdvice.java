package dev.maynestream.ledgify.account;

import build.buf.protovalidate.exceptions.ValidationException;
import build.buf.validate.Violation;
import io.grpc.Status;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.jooq.exception.NoDataFoundException;
import org.springframework.dao.DataIntegrityViolationException;

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

    @GrpcExceptionHandler(NoDataFoundException.class)
    public Status handleDataIntegrityViolation(NoDataFoundException e) {
        return Status.NOT_FOUND.withDescription("Entity not found").withCause(e);
    }

    @GrpcExceptionHandler(Throwable.class)
    public Status handleThrowable(Throwable e) {
        return Status.UNKNOWN.withDescription("Could not handle request").withCause(e);
    }
}
package dev.maynestream.ledgify.account.error;

import dev.maynestream.ledgify.error.GrpcExceptionAdvice;
import io.grpc.Status;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.jooq.exception.NoDataFoundException;

public class GrpcJooqExceptionAdvice extends GrpcExceptionAdvice {

    @GrpcExceptionHandler(NoDataFoundException.class)
    public Status handleNoDataFound(NoDataFoundException e) {
        return Status.NOT_FOUND.withDescription("Entity not found").withCause(e);
    }
}
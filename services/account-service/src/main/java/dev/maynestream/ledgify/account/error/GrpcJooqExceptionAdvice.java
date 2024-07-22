package dev.maynestream.ledgify.account.error;

import io.grpc.Status;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.jooq.exception.NoDataFoundException;

@GrpcAdvice
public class GrpcJooqExceptionAdvice {

    @GrpcExceptionHandler(NoDataFoundException.class)
    public Status handleNoDataFound(NoDataFoundException e) {
        return Status.NOT_FOUND.withDescription("Entity not found").withCause(e);
    }
}
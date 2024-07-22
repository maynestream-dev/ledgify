package dev.maynestream.ledgify.ledger.error;

import dev.maynestream.ledgify.error.GrpcExceptionAdvice;
import io.grpc.Status;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.apache.bookkeeper.client.BKException;

public class GrpcLedgerExceptionAdvice extends GrpcExceptionAdvice {

    @GrpcExceptionHandler({ BKException.BKNoSuchLedgerExistsOnMetadataServerException.class, BKException.BKNoSuchLedgerExistsException.class })
    public Status BKNoSuchLedgerExists(Throwable e) {
        return Status.NOT_FOUND.withDescription("Ledger does not exist: " + e.getMessage()).withCause(e);
    }
}
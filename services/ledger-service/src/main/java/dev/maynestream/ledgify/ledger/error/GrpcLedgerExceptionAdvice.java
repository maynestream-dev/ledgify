package dev.maynestream.ledgify.ledger.error;

import dev.maynestream.ledgify.error.GrpcExceptionAdvice;
import io.grpc.Status;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.apache.bookkeeper.client.BKException;

@GrpcAdvice
public class GrpcLedgerExceptionAdvice extends GrpcExceptionAdvice {

    @GrpcExceptionHandler(BKException.BKNoSuchLedgerExistsOnMetadataServerException.class)
    public Status handleBKNoSuchLedgerExistsOnMetadataServer(BKException.BKNoSuchLedgerExistsOnMetadataServerException e) {
        return Status.NOT_FOUND.withDescription("Ledger does not exist: " + e.getMessage()).withCause(e);
    }

    @GrpcExceptionHandler(BKException.BKNoSuchLedgerExistsException.class)
    public Status handleBKNoSuchLedgerExists(BKException.BKNoSuchLedgerExistsException e) {
        return Status.NOT_FOUND.withDescription("Ledger does not exist: " + e.getMessage()).withCause(e);
    }
}
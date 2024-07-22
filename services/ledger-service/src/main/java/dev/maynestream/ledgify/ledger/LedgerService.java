package dev.maynestream.ledgify.ledger;

import com.google.protobuf.Empty;
import dev.maynestream.ledgify.transaction.Transaction;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.bookkeeper.client.BookKeeper;
import org.apache.bookkeeper.client.api.DigestType;
import org.apache.bookkeeper.client.api.WriteHandle;

import java.util.Map;

import static dev.maynestream.ledgify.validation.ValidationService.validate;

@Slf4j
@GrpcService
class LedgerService extends LedgerGrpc.LedgerImplBase {

    private final BookKeeper bookKeeper;
    private final BookkeeperConfiguration bookkeeperConfiguration;

    LedgerService(BookKeeper bookKeeper, BookkeeperConfiguration bookkeeperConfiguration) {
        this.bookKeeper = bookKeeper;
        this.bookkeeperConfiguration = bookkeeperConfiguration;
    }

    @Override
    @SneakyThrows
    public void commitTransaction(Transaction request, StreamObserver<Empty> responseObserver) {
        validate(request);

        final WriteHandle ledger = bookKeeper.newCreateLedgerOp()
                                             .withDigestType(DigestType.DUMMY)
                                             .withPassword("password".getBytes()) // TODO obviously this isn't ideal
                                             .withEnsembleSize(bookkeeperConfiguration.getDefaultEnsembleSize())
                                             .withWriteQuorumSize(bookkeeperConfiguration.getDefaultWriteQuorumSize())
                                             .withAckQuorumSize(bookkeeperConfiguration.getDefaultAckQuorumSize())
                                             .withCustomMetadata(Map.of("account", request.toByteArray()))
                                             .execute()
                                             .get();

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}

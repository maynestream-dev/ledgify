package dev.maynestream.ledgify.ledger;

import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.bookkeeper.client.BookKeeper;
import org.apache.bookkeeper.client.LedgerHandle;
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
    public void createLedger(CreateLedgerRequest request, StreamObserver<CreateLedgerResponse> responseObserver) {
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
        final CreateLedgerResponse response = CreateLedgerResponse.newBuilder()
                                                                  .setLedgerId(ledger.getId())
                                                                  .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    @SneakyThrows
    public void appendEntry(LedgerEntryRequest request, StreamObserver<LedgerEntryResponse> responseObserver) {
        validate(request);

        final LedgerHandle ledgerHandle = bookKeeper.openLedger(request.getLedgerId(),
                                                                BookKeeper.DigestType.DUMMY,
                                                                "password".getBytes());
        final long ledgerEntryId = ledgerHandle.addEntry(request.toByteArray());
        final LedgerEntryResponse response = LedgerEntryResponse.newBuilder()
                                                                .setLedgerEntryId(ledgerEntryId)
                                                                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}

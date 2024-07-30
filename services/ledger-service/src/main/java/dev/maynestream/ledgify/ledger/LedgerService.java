package dev.maynestream.ledgify.ledger;

import dev.maynestream.ledgify.conversion.ConversionService;
import dev.maynestream.ledgify.ledger.transaction.TransactionCoordinator;
import dev.maynestream.ledgify.transaction.ListTransactionsRequest;
import dev.maynestream.ledgify.transaction.ListTransactionsResponse;
import dev.maynestream.ledgify.transaction.Transaction;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.SortedSet;
import java.util.UUID;

import static dev.maynestream.ledgify.validation.ValidationService.validate;

@Slf4j
@GrpcService
class LedgerService extends LedgerGrpc.LedgerImplBase {

    private final TransactionCoordinator transactionCoordinator;

    LedgerService(final TransactionCoordinator transactionCoordinator) {
        this.transactionCoordinator = transactionCoordinator;
    }

    @Override
    @SneakyThrows
    public void commitTransaction(LedgerCommitRequest request, StreamObserver<LedgerCommitResponse> responseObserver) {
        validate(request);

        final LedgerCommitResponse response = transactionCoordinator.routeTransaction(accountId(request.getAccountId()),
                                                                                      request.getTransaction());

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    @SneakyThrows
    public void listTransactions(final ListTransactionsRequest request,
                                 final StreamObserver<ListTransactionsResponse> responseObserver) {
        validate(request);

        final SortedSet<Transaction> transactions = transactionCoordinator.listTransactions(accountId(request.getAccountId()));
        final ListTransactionsResponse response = ListTransactionsResponse.newBuilder()
                                                                          .addAllTransactions(transactions)
                                                                          .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private static UUID accountId(final String accountId) {
        return ConversionService.getUuid(accountId, "accountId");
    }
}

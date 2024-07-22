package dev.maynestream.ledgify.transaction;

import dev.maynestream.ledgify.domain.tables.records.TransactionRecord;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

import static dev.maynestream.ledgify.conversion.ConversionService.convertBigDecimal;
import static dev.maynestream.ledgify.conversion.ConversionService.getCurrency;
import static dev.maynestream.ledgify.conversion.ConversionService.getUuid;
import static dev.maynestream.ledgify.validation.ValidationService.validate;

@Slf4j
@GrpcService
class TransactionLogService extends TransactionLogGrpc.TransactionLogImplBase {

    private final TransactionRepository transactionRepository;

    public TransactionLogService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    @SneakyThrows
    public void submitTransaction(TransactionDetails request, StreamObserver<Transaction> responseObserver) {
        validate(request);

        final TransactionRecord transaction = transactionRepository.submitTransaction(request.getDescription(),
                                                                                      getAccountId(request.getDebitAccountId()),
                                                                                      getAccountId(request.getCreditAccountId()),
                                                                                      getCurrency(request.getCurrency()),
                                                                                      convertBigDecimal(request.getAmount()));

        final Transaction response = buildTransaction(transaction);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    @SneakyThrows
    public void commitTransaction(TransactionCommit request, StreamObserver<Transaction> responseObserver) {
        validate(request);

        final TransactionRecord transaction = transactionRepository.updateTransactionState(getTransactionId(request.getTransactionId()),
                                                                                           request.getState()
                                                                                                  .getStatus()
                                                                                                  .name(),
                                                                                           request.getState()
                                                                                                  .getContext());
        final Transaction response = buildTransaction(transaction);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    @SneakyThrows
    public void listTransactions(ListTransactionsRequest request, StreamObserver<ListTransactionsResponse> responseObserver) {
        validate(request);

        final Collection<TransactionRecord> transactions = transactionRepository.listTransactions(getAccountId(request.getAccountId()));
        final ListTransactionsResponse response = ListTransactionsResponse.newBuilder()
                                                                          .addAllTransactions(transactions.stream()
                                                                                                          .map(this::buildTransaction)
                                                                                                          .toList())
                                                                          .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private Transaction buildTransaction(TransactionRecord transaction) {
        return Transaction.newBuilder()
                          .setTransactionId(transaction.getId().toString())
                          .setDetails(TransactionDetails.newBuilder()
                                                        .setDescription(getDescription(transaction))
                                                        .setCurrency(transaction.getCurrency())
                                                        .setAmount(convertBigDecimal(transaction.getAmount()))
                                                        .setDebitAccountId(transaction.getDebitAccountId().toString())
                                                        .setCreditAccountId(transaction.getCreditAccountId().toString())
                                                        .build())
                          .setCommitState(TransactionCommitState.newBuilder()
                                                                .setStatus(TransactionCommitStatus.valueOf(transaction.getState()))
                                                                .setContext(getStateContext(transaction))
                                                                .build())
                          .build();
    }

    private static UUID getTransactionId(String transactionId) {
        return getUuid(transactionId, "Transaction ID");
    }

    private static UUID getAccountId(String customerId) {
        return getUuid(customerId, "Account ID");
    }

    private static String getDescription(TransactionRecord transaction) {
        return Objects.toString(transaction.getDescription(), "");
    }

    private static String getStateContext(TransactionRecord transaction) {
        return Objects.toString(transaction.getStateContext(), "");
    }
}

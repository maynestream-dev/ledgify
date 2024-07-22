package dev.maynestream.ledgify.transaction;

import com.google.protobuf.Empty;
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
class TransactionService extends TransactionGrpc.TransactionImplBase {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    @SneakyThrows
    public void submitTransaction(TransactionDetails request, StreamObserver<TransactionStatus> responseObserver) {
        validate(request);

        final TransactionRecord transaction = transactionRepository.submitTransaction(request.getDescription(),
                                                                                      getAccountId(request.getDebit()
                                                                                                          .getAccountId()),
                                                                                      getAccountId(request.getCredit()
                                                                                                          .getAccountId()),
                                                                                      request.getDebit()
                                                                                             .getLedgerEntryId(),
                                                                                      request.getCredit()
                                                                                             .getLedgerEntryId(),
                                                                                      getCurrency(request.getCurrency()),
                                                                                      convertBigDecimal(request.getAmount()));

        final TransactionStatus response = buildTransactionStatus(transaction);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    @SneakyThrows
    public void completeTransaction(TransactionEvent request, StreamObserver<TransactionStatus> responseObserver) {
        validate(request);

        final TransactionRecord transaction = transactionRepository.updateTransactionState(getTransactionId(request),
                                                                                           TransactionState.COMPLETED.name(),
                                                                                           request.getStateContext());
        final TransactionStatus response = buildTransactionStatus(transaction);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    @SneakyThrows
    public void failTransaction(TransactionEvent request, StreamObserver<TransactionStatus> responseObserver) {
        validate(request);

        final TransactionRecord transaction = transactionRepository.updateTransactionState(getTransactionId(request),
                                                                                           TransactionState.FAILED.name(),
                                                                                           request.getStateContext());
        final TransactionStatus response = buildTransactionStatus(transaction);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    @SneakyThrows
    public void listTransactions(Empty request, StreamObserver<ListTransactionsResponse> responseObserver) {
        validate(request);

        final Collection<TransactionRecord> transactions = transactionRepository.listTransactions();
        final ListTransactionsResponse response = ListTransactionsResponse.newBuilder()
                                                                          .addAllTransactions(transactions.stream()
                                                                                                          .map(this::mapTransaction)
                                                                                                          .toList())
                                                                          .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private ListTransactionsResponse.Transaction mapTransaction(TransactionRecord transaction) {
        return ListTransactionsResponse.Transaction.newBuilder()
                                                   .setTransactionId(transaction.getId().toString())
                                                   .setDescription(getDescription(transaction))
                                                   .setCurrency(transaction.getCurrency())
                                                   .setAmount(convertBigDecimal(transaction.getAmount()))
                                                   .setDebit(BalanceAdjustment.newBuilder()
                                                                              .setAccountId(transaction.getDebitAccountId()
                                                                                                       .toString())
                                                                              .setLedgerEntryId(transaction.getDebitLedgerEntryId())
                                                                              .build())
                                                   .setCredit(BalanceAdjustment.newBuilder()
                                                                               .setAccountId(transaction.getCreditAccountId()
                                                                                                        .toString())
                                                                               .setLedgerEntryId(transaction.getCreditLedgerEntryId())
                                                                               .build())
                                                   .setState(TransactionState.valueOf(transaction.getState()))
                                                   .setStateContext(getStateContext(transaction))
                                                   .build();
    }

    private static TransactionStatus buildTransactionStatus(TransactionRecord transaction) {
        return TransactionStatus.newBuilder()
                                .setTransactionId(transaction.getId().toString())
                                .setState(TransactionState.valueOf(transaction.getState()))
                                .setStateContext(getStateContext(transaction))
                                .build();
    }

    private static UUID getTransactionId(TransactionEvent request) {
        return getUuid(request.getTransactionId(), "Transaction ID");
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

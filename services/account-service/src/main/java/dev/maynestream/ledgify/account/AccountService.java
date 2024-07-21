package dev.maynestream.ledgify.account;

import dev.maynestream.ledgify.account.tables.records.AccountRecord;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Currency;
import java.util.UUID;

import static dev.maynestream.ledgify.conversion.ConversionService.convertBigDecimal;
import static dev.maynestream.ledgify.conversion.ConversionService.getCurrency;
import static dev.maynestream.ledgify.conversion.ConversionService.getUuid;
import static dev.maynestream.ledgify.validation.ValidationService.validate;

@Slf4j
@GrpcService
class AccountService extends AccountGrpc.AccountImplBase {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @SneakyThrows
    public void createAccount(CreateAccountRequest request, StreamObserver<CreateAccountResponse> responseObserver) {
        validate(request);

        final Currency currency = getCurrency(request.getCurrency());
        final AccountRecord account = accountRepository.createAccount(getCustomerId(request.getCustomerId()), request.getLedgerId(), currency);
        final CreateAccountResponse response = CreateAccountResponse.newBuilder().setId(account.getId().toString()).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    @SneakyThrows
    public void creditAccount(BalanceAdjustment request, StreamObserver<AccountBalance> responseObserver) {
        validate(request);

        final AccountRecord account = accountRepository.creditAccount(getAccountId(request.getAccountId()), convertBigDecimal(request.getAmount()));
        final AccountBalance response = AccountBalance.newBuilder()
                .setAccountId(request.getAccountId())
                .setCurrency(account.getCurrency())
                .setBalance(convertBigDecimal(account.getBalance())).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    @SneakyThrows
    public void debitAccount(BalanceAdjustment request, StreamObserver<AccountBalance> responseObserver) {
        validate(request);

        final AccountRecord account = accountRepository.debitAccount(getAccountId(request.getAccountId()), convertBigDecimal(request.getAmount()));
        final AccountBalance response = AccountBalance.newBuilder()
                .setAccountId(request.getAccountId())
                .setCurrency(account.getCurrency())
                .setBalance(convertBigDecimal(account.getBalance())).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private static UUID getAccountId(String customerId) {
        return getUuid(customerId, "Account ID");
    }

    private static UUID getCustomerId(String customerId) {
        return getUuid(customerId, "Customer ID");
    }
}

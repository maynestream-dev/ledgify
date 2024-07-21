package dev.maynestream.ledgify.account;

import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.Validator;
import build.buf.protovalidate.exceptions.ValidationException;
import com.google.protobuf.Message;
import dev.maynestream.ledgify.account.tables.records.AccountRecord;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

@Slf4j
@GrpcService
class AccountService extends AccountGrpc.AccountImplBase {

    private static final Validator VALIDATOR = new Validator();

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

    private static <T extends Message> void validate(T request) throws ValidationException {
        final ValidationResult validationResult = VALIDATOR.validate(request);
        if (!validationResult.isSuccess()) {
            throw new ConstraintViolationException("Request has constraint violations", validationResult.getViolations());
        }
    }

    private static UUID getAccountId(String customerId) {
        return getUuid(customerId, "Account ID");
    }

    private static UUID getCustomerId(String customerId) {
        return getUuid(customerId, "Customer ID");
    }

    private static UUID getUuid(String customerId, String s) {
        try {
            return UUID.fromString(customerId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + s + " (UUID): " + customerId);
        }
    }

    private static Currency getCurrency(String currency) {
        try {
            return Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid currency: " + currency);
        }
    }

    private static String convertBigDecimal(BigDecimal amount) {
        try {
            return amount.toString();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount: " + amount);
        }
    }

    private static BigDecimal convertBigDecimal(String amount) {
        try {
            return new java.math.BigDecimal(amount);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount: " + amount);
        }
    }
}

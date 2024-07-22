package dev.maynestream.ledgify.account;

import dev.maynestream.ledgify.domain.tables.records.AccountRecord;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Collection;
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
        final AccountRecord account = accountRepository.createAccount(getCustomerId(request.getCustomerId()),
                                                                      request.getLedgerId(),
                                                                      currency);
        final CreateAccountResponse response = CreateAccountResponse.newBuilder()
                                                                    .setAccountId(account.getId().toString())
                                                                    .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    @SneakyThrows
    public void listAccounts(ListAccountsRequest request, StreamObserver<ListAccountsResponse> responseObserver) {
        validate(request);

        final Collection<AccountRecord> accounts = accountRepository.listAccounts(getCustomerId(request.getCustomerId()));
        final ListAccountsResponse response = ListAccountsResponse.newBuilder()
                                                                  .addAllAccounts(accounts.stream()
                                                                                          .map(AccountService::mapAccount)
                                                                                          .toList())
                                                                  .setCustomerId(request.getCustomerId())
                                                                  .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private static ListAccountsResponse.Account mapAccount(AccountRecord accountR) {
        return ListAccountsResponse.Account.newBuilder()
                                           .setAccountId(accountR.getId().toString())
                                           .setLedgerId(accountR.getLedgerId())
                                           .setCurrency(accountR.getCurrency())
                                           .setBalance(convertBigDecimal(accountR.getBalance()))
                                           .build();
    }

    private static UUID getCustomerId(String customerId) {
        return getUuid(customerId, "Customer ID");
    }
}

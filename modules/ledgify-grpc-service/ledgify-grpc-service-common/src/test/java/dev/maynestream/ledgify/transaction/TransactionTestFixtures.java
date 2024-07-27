package dev.maynestream.ledgify.transaction;

import static dev.maynestream.ledgify.CommonTestFixtures.randomBigDecimal;
import static dev.maynestream.ledgify.CommonTestFixtures.randomCurrency;
import static dev.maynestream.ledgify.CommonTestFixtures.randomEnum;
import static dev.maynestream.ledgify.CommonTestFixtures.randomIdString;
import static dev.maynestream.ledgify.CommonTestFixtures.randomText;
import static dev.maynestream.ledgify.conversion.ConversionService.convertBigDecimal;

public class TransactionTestFixtures {
    public static Transaction.Builder transaction() {
        return Transaction.newBuilder()
                          .setTransactionId(randomIdString())
                          .setDetails(transactionDetails())
                          .setCommitState(transactionCommitState());
    }

    public static TransactionCommitState.Builder transactionCommitState() {
        return TransactionCommitState.newBuilder()
                                     .setStatus(randomEnum(TransactionCommitStatus.class))
                                     .setContext(randomText());
    }

    public static TransactionDetails.Builder transactionDetails() {
        return TransactionDetails.newBuilder()
                                 .setDescription(randomText())
                                 .setCurrency(randomCurrency().getCurrencyCode())
                                 .setAmount(convertBigDecimal(randomBigDecimal()))
                                 .setDebitAccountId(randomIdString())
                                 .setCreditAccountId(randomIdString());
    }
}

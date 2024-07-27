package dev.maynestream.ledgify;

import com.google.protobuf.ProtocolMessageEnum;
import org.apache.commons.lang3.RandomStringUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CommonTestFixtures {

    public static UUID randomId() {
        return UUID.randomUUID();
    }

    public static String randomIdString() {
        return randomId().toString();
    }

    public static BigDecimal randomBigDecimal() {
        return BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble());
    }

    public static String randomText() {
        return RandomStringUtils.randomAlphabetic(20);
    }

    public static Currency randomCurrency() {
        return randomElement(Currency.getAvailableCurrencies());
    }

    public static <T> T randomElement(Collection<T> coll) {
        return List.copyOf(coll).get(ThreadLocalRandom.current().nextInt(coll.size()));
    }

    public static <T extends Enum<T>> T randomEnum(final Class<T> enumClass) {
        final T[] values = enumClass.getEnumConstants();
        int valueCount = values.length;
        if (ProtocolMessageEnum.class.isAssignableFrom(enumClass)) {
            valueCount -= 2; // the last 2 values in proto enum are UNKNOWN and UNRECOGNISED
        }
        return values[ThreadLocalRandom.current().nextInt(valueCount)];
    }
}

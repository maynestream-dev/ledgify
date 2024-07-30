package dev.maynestream.ledgify.conversion;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

public interface ConversionService {

    static UUID getUuid(String idString, String uuidType) {
        try {
            return UUID.fromString(idString);
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + uuidType + " (UUID): " + idString);
        }
    }

    static Currency getCurrency(String currency) {
        try {
            return Currency.getInstance(currency);
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid currency: " + currency);
        }
    }

    static String convertBigDecimal(BigDecimal amount) {
        try {
            return amount.toString();
        } catch (NullPointerException | NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount: " + amount);
        }
    }

    static BigDecimal convertBigDecimal(String amount) {
        try {
            return new java.math.BigDecimal(amount);
        } catch (NullPointerException | NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount: " + amount);
        }
    }
}

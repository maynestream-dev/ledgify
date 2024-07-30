package dev.maynestream.ledgify.conversion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static dev.maynestream.ledgify.CommonTestFixtures.randomBigDecimal;
import static dev.maynestream.ledgify.CommonTestFixtures.randomCurrency;
import static dev.maynestream.ledgify.CommonTestFixtures.randomText;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConversionServiceTest {

    @Test
    void shouldReturnValidUuid() {
        // given
        final UUID id = UUID.randomUUID();

        // when
        final UUID testId = ConversionService.getUuid(id.toString(), "test ID");

        // then
        assertThat(testId, equalTo(id));
    }

    @Test
    void shouldThrowExceptionWhenInvalidUuidString() {
        // given
        final String id = randomText();

        // when
        final Executable convert = () -> ConversionService.getUuid(id, "test ID");

        // then
        assertThrows(IllegalArgumentException.class, convert);
    }

    @Test
    void shouldReturnValidCurrency() {
        // given
        final Currency expected = randomCurrency();

        // when
        final Currency actual = ConversionService.getCurrency(expected.getCurrencyCode());

        // then
        assertThat(actual, equalTo(expected));
    }

    @Test
    void shouldThrowExceptionWhenInvalidCurrencyCode() {
        // given
        final String currency = "NOPE";

        // when
        final Executable convert = () -> ConversionService.getCurrency(currency);

        // then
        assertThrows(IllegalArgumentException.class, convert);
    }

    @Test
    void shouldReturnValidBigDecimal() {
        // given
        final BigDecimal expected = randomBigDecimal();

        // when
        final BigDecimal actual = ConversionService.convertBigDecimal(expected.toPlainString());

        // then
        assertThat(actual, equalTo(expected));
    }

    @Test
    void shouldThrowExceptionWhenInvalidBigDecimalString() {
        // given
        final String bigDecimal = "NOPE";

        // when
        final Executable convert = () -> ConversionService.convertBigDecimal(bigDecimal);

        // then
        assertThrows(IllegalArgumentException.class, convert);
    }

    @Test
    void shouldReturnValidBigDecimalString() {
        // given
        final BigDecimal expected = randomBigDecimal();

        // when
        final BigDecimal actual = ConversionService.convertBigDecimal(expected.toPlainString());

        // then
        assertThat(actual, equalTo(expected));
    }

    @Test
    void shouldThrowExceptionWhenInvalidBigDecimal() {
        // given
        final BigDecimal bigDecimal = null;

        // when
        final Executable convert = () -> ConversionService.convertBigDecimal(bigDecimal);

        // then
        assertThrows(IllegalArgumentException.class, convert);
    }
}
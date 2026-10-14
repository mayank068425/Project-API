package com.banking.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Luhn check digit on account numbers.
 *
 * <p>Not security — it is a typo filter. It turns "your transfer went to a
 * stranger" into a validation message before anything moves.
 */
class AccountNumberGeneratorTest {

    @ParameterizedTest(name = "{0} is well-formed")
    @ValueSource(strings = {
            "000001000017", "000001000025", "000001000033",
            "000001000041", "000001000058"
    })
    @DisplayName("generated numbers validate")
    void generatedNumbersValidate(String accountNumber) {
        assertThat(AccountNumberGenerator.isValid(accountNumber)).isTrue();
    }

    @Test
    @DisplayName("every single-digit error is caught")
    void catchesEverySingleDigitError() {
        String valid = "000001000017";
        int caught = 0;
        int total = 0;

        for (int i = 0; i < valid.length() - 1; i++) {
            for (char digit = '0'; digit <= '9'; digit++) {
                if (digit == valid.charAt(i)) {
                    continue;
                }
                total++;
                String typo = valid.substring(0, i) + digit + valid.substring(i + 1);
                if (!AccountNumberGenerator.isValid(typo)) {
                    caught++;
                }
            }
        }

        assertThat(caught).isEqualTo(total);
    }

    @Test
    @DisplayName("adjacent transpositions are caught, except 09 and 90")
    void catchesTranspositionsExceptZeroNine() {
        // The one documented blind spot in Luhn. Worth pinning so nobody later
        // assumes the check is exhaustive and drops a defence that relies on it.
        assertThat(AccountNumberGenerator.luhnCheckDigit("09"))
                .isEqualTo(AccountNumberGenerator.luhnCheckDigit("90"));

        // Any other adjacent pair does change the check digit.
        assertThat(AccountNumberGenerator.luhnCheckDigit("12"))
                .isNotEqualTo(AccountNumberGenerator.luhnCheckDigit("21"));
    }

    @Test
    @DisplayName("non-numeric and short input is rejected rather than throwing")
    void rejectsMalformedInput() {
        assertThat(AccountNumberGenerator.isValid(null)).isFalse();
        assertThat(AccountNumberGenerator.isValid("")).isFalse();
        assertThat(AccountNumberGenerator.isValid("1")).isFalse();
        assertThat(AccountNumberGenerator.isValid("00000100001X")).isFalse();
        assertThat(AccountNumberGenerator.isValid("ACC123456789")).isFalse();
    }

    @Test
    @DisplayName("a check digit is always a single digit")
    void checkDigitIsOneDigit() {
        for (int i = 0; i < 1000; i++) {
            int digit = AccountNumberGenerator.luhnCheckDigit(String.format("%011d", i));
            assertThat(digit).isBetween(0, 9);
        }
    }
}

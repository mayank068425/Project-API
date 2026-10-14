package com.example.lms.validation;

import com.example.lms.dto.request.BookRequest;
import com.example.lms.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookValidatorTest {

    private BookValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BookValidator();
    }

    private BookRequest request(String isbn, String title, Long authorId, Long categoryId,
                                Integer copies, Integer year) {
        return new BookRequest(isbn, title, authorId, categoryId, null, null, null, null,
                year, copies, BigDecimal.TEN, null);
    }

    @ParameterizedTest(name = "{0} has a valid check digit")
    @ValueSource(strings = {
            "9780132350884", "9780134685991", "9781449373320",
            "978-0-13-235088-4", "043942089X"
    })
    @DisplayName("real ISBNs pass, hyphenated or not")
    void acceptsValidIsbns(String isbn) {
        assertThat(validator.isChecksumValid(isbn)).isTrue();
    }

    @Test
    @DisplayName("a transposed pair of digits is caught")
    void catchesTransposition() {
        // Passes the regex on the DTO and fails here, which is the realistic
        // error when someone types an ISBN off a copyright page.
        assertThat(validator.isChecksumValid("9780132350884")).isTrue();
        assertThat(validator.isChecksumValid("9780132530884")).isFalse();
    }

    @Test
    @DisplayName("a single wrong digit is caught")
    void catchesSingleDigitTypo() {
        assertThat(validator.isChecksumValid("9780132350885")).isFalse();
    }

    @Test
    @DisplayName("ISBN-10 accepts X in the check position")
    void acceptsXCheckDigit() {
        assertThat(validator.isChecksumValid("043942089X")).isTrue();
        assertThat(validator.isChecksumValid("0439420891")).isFalse();
    }

    @Test
    @DisplayName("create requires the fields update leaves optional")
    void createRequiresCoreFields() {
        assertThatThrownBy(() -> validator.validateForCreate(
                request(null, "Title", 1L, 1L, 1, 2020)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ISBN");

        assertThatThrownBy(() -> validator.validateForCreate(
                request("9780132350884", "Title", null, 1L, 1, 2020)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("author");

        assertThatThrownBy(() -> validator.validateForCreate(
                request("9780132350884", "Title", 1L, 1L, 0, 2020)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("at least one");
    }

    @Test
    @DisplayName("a publication year before printing existed is refused")
    void refusesImpossibleYear() {
        assertThatThrownBy(() -> validator.validateForCreate(
                request("9780132350884", "Title", 1L, 1L, 1, 1200)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Publication year");
    }

    @Test
    @DisplayName("next year is allowed, because December books carry the next year")
    void allowsNextYear() {
        int nextYear = java.time.Year.now().getValue() + 1;
        validator.validateForCreate(request("9780132350884", "Title", 1L, 1L, 1, nextYear));

        assertThatThrownBy(() -> validator.validateForCreate(
                request("9780132350884", "Title", 1L, 1L, 1, nextYear + 5)))
                .isInstanceOf(BusinessRuleException.class);
    }
}

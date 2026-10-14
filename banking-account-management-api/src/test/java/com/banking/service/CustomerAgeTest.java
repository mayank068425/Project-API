package com.banking.service;

import com.banking.entity.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Age is derived, never stored.
 *
 * <p>A stored age is correct the day it is written and wrong from the customer's
 * next birthday onwards. Here it gates account opening, so a stale value would
 * eventually let a minor open an unrestricted account — and nothing would flag it.
 */
class CustomerAgeTest {

    private Customer bornOn(LocalDate date) {
        return Customer.builder().id(1L).firstName("Test").lastName("User")
                .dateOfBirth(date).build();
    }

    @Test
    @DisplayName("age is whole years elapsed")
    void ageIsWholeYears() {
        Customer customer = bornOn(LocalDate.of(2000, 6, 15));

        assertThat(customer.ageOn(LocalDate.of(2026, 6, 14))).isEqualTo(25);
        assertThat(customer.ageOn(LocalDate.of(2026, 6, 15))).isEqualTo(26);
        assertThat(customer.ageOn(LocalDate.of(2026, 6, 16))).isEqualTo(26);
    }

    @Test
    @DisplayName("the day before an 18th birthday is still 17")
    void eighteenthBirthdayBoundary() {
        // The boundary that decides whether an account can be opened at all.
        Customer customer = bornOn(LocalDate.of(2008, 7, 29));

        assertThat(customer.ageOn(LocalDate.of(2026, 7, 28))).isEqualTo(17);
        assertThat(customer.ageOn(LocalDate.of(2026, 7, 29))).isEqualTo(18);
    }

    @Test
    @DisplayName("a 29 February birthday resolves on 1 March in a common year")
    void leapDayBirthday() {
        // Period.between handles this correctly; the test exists because a
        // hand-rolled day-difference divided by 365 does not, and somebody
        // eventually tries that.
        Customer leapling = bornOn(LocalDate.of(2004, 2, 29));

        assertThat(leapling.ageOn(LocalDate.of(2026, 2, 28))).isEqualTo(21);
        assertThat(leapling.ageOn(LocalDate.of(2026, 3, 1))).isEqualTo(22);
        // In a leap year the birthday itself exists.
        assertThat(leapling.ageOn(LocalDate.of(2028, 2, 29))).isEqualTo(24);
    }

    @Test
    @DisplayName("a date before birth is an error, not a negative age")
    void refusesDateBeforeBirth() {
        assertThatThrownBy(() -> bornOn(LocalDate.of(2000, 1, 1))
                .ageOn(LocalDate.of(1999, 12, 31)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a customer with no date of birth fails loudly")
    void requiresDateOfBirth() {
        assertThatThrownBy(() -> Customer.builder().id(1L).build().getAge())
                .isInstanceOf(IllegalStateException.class);
    }
}

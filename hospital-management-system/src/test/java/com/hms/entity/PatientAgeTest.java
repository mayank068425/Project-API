package com.hms.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Age derivation.
 *
 * <p>The specification listed both {@code age} and {@code date_of_birth}. Only
 * the date is stored, and these tests are why: a stored age is correct on the
 * day it is written and wrong from the next birthday onwards.
 */
class PatientAgeTest {

    private Patient bornOn(LocalDate dateOfBirth) {
        return Patient.builder()
                .firstName("Test").lastName("Patient")
                .dateOfBirth(dateOfBirth)
                .gender(Gender.OTHER)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .phone("9812345670")
                .build();
    }

    @Test
    @DisplayName("a stored age would go stale; a derived one does not")
    void derivedAgeTracksTime() {
        LocalDate dob = LocalDate.of(1991, 3, 14);
        Patient patient = bornOn(dob);

        // Registered in 2021 at 30. Five years later the same row reads 35,
        // because nothing was stored to go stale.
        assertThat(patient.ageOn(LocalDate.of(2021, 6, 1))).isEqualTo(30);
        assertThat(patient.ageOn(LocalDate.of(2026, 6, 1))).isEqualTo(35);
    }

    @ParameterizedTest(name = "born {0}, on {1} -> age {2}")
    @CsvSource({
            "2000-06-15, 2026-06-14, 25",   // the day before
            "2000-06-15, 2026-06-15, 26",   // on the birthday, already older
            "2000-06-15, 2026-06-16, 26",
            "2026-07-29, 2026-07-29, 0"     // newborn
    })
    @DisplayName("the birthday boundary")
    void birthdayBoundary(String dob, String on, int expected) {
        assertThat(bornOn(LocalDate.parse(dob)).ageOn(LocalDate.parse(on)))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "leap birthday, on {0} -> age {1}")
    @CsvSource({
            "2026-02-28, 25",   // non-leap year: not yet older on the 28th
            "2026-03-01, 26",   // older from 1 March
            "2028-02-29, 28"    // leap year: older on the actual date
    })
    @DisplayName("a 29 February birthday needs no special casing")
    void leapYearBirthday(String on, int expected) {
        assertThat(bornOn(LocalDate.of(2000, 2, 29)).ageOn(LocalDate.parse(on)))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("minority is derived too, so it flips on the eighteenth birthday")
    void minorityFollowsAge() {
        Patient patient = bornOn(LocalDate.of(2008, 5, 20));

        assertThat(patient.ageOn(LocalDate.of(2026, 5, 19))).isEqualTo(17);
        assertThat(patient.ageOn(LocalDate.of(2026, 5, 20))).isEqualTo(18);
    }

    @Test
    @DisplayName("a date before the patient was born is rejected, not negative")
    void refusesDateBeforeBirth() {
        Patient patient = bornOn(LocalDate.of(2000, 1, 1));

        assertThatThrownBy(() -> patient.ageOn(LocalDate.of(1999, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a patient with no date of birth cannot report an age")
    void refusesMissingDateOfBirth() {
        Patient patient = bornOn(null);

        assertThatThrownBy(patient::getAge).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fullNameJoinsBothParts() {
        Patient patient = bornOn(LocalDate.of(1990, 1, 1));
        patient.setFirstName("Priya");
        patient.setLastName("Sharma");

        assertThat(patient.getFullName()).isEqualTo("Priya Sharma");
    }
}

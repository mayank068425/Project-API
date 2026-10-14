package com.hms.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BloodGroupTest {

    @ParameterizedTest(name = "'{0}' parses to {1}")
    @CsvSource({
            "O+,   O_POSITIVE",
            "o+,   O_POSITIVE",
            "' O+ ', O_POSITIVE",
            "AB-,  AB_NEGATIVE",
            "ab-,  AB_NEGATIVE",
            "A_POSITIVE, A_POSITIVE"
    })
    @DisplayName("clinical notation from a form is accepted")
    void parsesLabels(String input, BloodGroup expected) {
        assertThat(BloodGroup.fromLabel(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a zero typed for the letter O is rejected rather than stored")
    void rejectsZeroForO() {
        // The reason this is an enum and not a VARCHAR. A free-text column
        // accepts "0+" happily, and it is indistinguishable from "O+" at a
        // glance in exactly the situation where it is read fastest.
        assertThatThrownBy(() -> BloodGroup.fromLabel("0+"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonsense() {
        assertThatThrownBy(() -> BloodGroup.fromLabel("C+"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(BloodGroup.fromLabel(null)).isNull();
    }

    @Test
    void everyGroupHasALabel() {
        for (BloodGroup group : BloodGroup.values()) {
            assertThat(group.getLabel()).isNotBlank();
            assertThat(BloodGroup.fromLabel(group.getLabel())).isEqualTo(group);
        }
    }
}

package com.example.lms.service;

import com.example.lms.config.LibraryPolicy;
import com.example.lms.util.DateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FineCalculatorTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate DUE = LocalDate.of(2026, 7, 10);
    private static final BigDecimal REPLACEMENT = new BigDecimal("45.00");

    private LibraryPolicy policy;
    private FineCalculator calculator;

    @BeforeEach
    void setUp() {
        policy = new LibraryPolicy();
        policy.setZone(IST);
        policy.setFinePerDay(new BigDecimal("2.00"));
        policy.setGraceDays(0);
        policy.setFineCapMultiplier(BigDecimal.ONE);
        policy.setDefaultReplacementCost(new BigDecimal("500.00"));
        calculator = new FineCalculator(policy);
    }

    @Test
    @DisplayName("the stated rule: due the 10th, returned the 15th, 2 a day = 10")
    void matchesTheStatedRule() {
        FineCalculator.Assessment assessment =
                calculator.assess(DUE, LocalDate.of(2026, 7, 15), REPLACEMENT);

        assertThat(assessment.chargeableDays()).isEqualTo(5);
        assertThat(assessment.amount()).isEqualByComparingTo("10.00");
        assertThat(assessment.capped()).isFalse();
    }

    @Nested
    @DisplayName("boundaries")
    class Boundaries {

        @ParameterizedTest(name = "returned {0} -> {1} days, fine {2}")
        @CsvSource({
                "2026-07-08, 0, 0.00",
                "2026-07-09, 0, 0.00",
                "2026-07-10, 0, 0.00",
                "2026-07-11, 1, 2.00",
                "2026-07-15, 5, 10.00"
        })
        void chargesFromTheDayAfterTheDueDate(String returned, int days, String fine) {
            FineCalculator.Assessment assessment =
                    calculator.assess(DUE, LocalDate.parse(returned), REPLACEMENT);

            assertThat(assessment.chargeableDays()).isEqualTo(days);
            assertThat(assessment.amount()).isEqualByComparingTo(fine);
        }

        @Test
        @DisplayName("returning on the due date is not late")
        void onTheDueDateIsNotLate() {
            // The off-by-one that fines every punctual member. First thing users
            // notice, last thing anyone tests.
            FineCalculator.Assessment assessment = calculator.assess(DUE, DUE, REPLACEMENT);

            assertThat(assessment.rawDaysLate()).isZero();
            assertThat(assessment.isChargeable()).isFalse();
            assertThat(assessment.explain()).contains("No fine");
        }

        @Test
        @DisplayName("an early return is not a credit")
        void earlyReturnIsNotACredit() {
            FineCalculator.Assessment assessment =
                    calculator.assess(DUE, DUE.minusDays(6), REPLACEMENT);

            assertThat(assessment.rawDaysLate()).isEqualTo(-6);
            assertThat(assessment.chargeableDays()).isZero();
            assertThat(assessment.amount()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("timezone")
    class Timezone {

        @Test
        @DisplayName("23:30 local on the due date is on time")
        void lateEveningReturnIsOnTime() {
            Instant moment = LocalDateTime.of(2026, 7, 10, 23, 30).atZone(IST).toInstant();

            assertThat(DateUtil.toLocalDate(moment, IST)).isEqualTo(DUE);
            assertThat(calculator.assess(DUE, DateUtil.toLocalDate(moment, IST), REPLACEMENT)
                    .isChargeable()).isFalse();
        }

        @Test
        @DisplayName("the same instant gives different dates, and different fines, per zone")
        void zoneChangesTheAnswer() {
            // 01:30 IST on the 11th is 20:00 UTC on the 10th. One zone says a day
            // late, the other says on time, and they disagree for 5.5 hours out
            // of every 24. This is why DateUtil has no overload that defaults the
            // zone.
            Instant moment = LocalDateTime.of(2026, 7, 11, 1, 30).atZone(IST).toInstant();

            LocalDate inIst = DateUtil.toLocalDate(moment, IST);
            LocalDate inUtc = DateUtil.toLocalDate(moment, ZoneOffset.UTC);

            assertThat(inIst).isEqualTo(LocalDate.of(2026, 7, 11));
            assertThat(inUtc).isEqualTo(LocalDate.of(2026, 7, 10));

            assertThat(calculator.assess(DUE, inIst, REPLACEMENT).amount())
                    .isEqualByComparingTo("2.00");
            assertThat(calculator.assess(DUE, inUtc, REPLACEMENT).amount())
                    .isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("cap and grace")
    class CapAndGrace {

        @Test
        @DisplayName("the fine stops growing at the replacement cost")
        void capsAtReplacementCost() {
            BigDecimal cheap = new BigDecimal("25.00");

            FineCalculator.Assessment under = calculator.assess(DUE, DUE.plusDays(12), cheap);
            assertThat(under.amount()).isEqualByComparingTo("24.00");
            assertThat(under.capped()).isFalse();

            FineCalculator.Assessment over = calculator.assess(DUE, DUE.plusDays(60), cheap);
            assertThat(over.calculatedAmount()).isEqualByComparingTo("120.00");
            assertThat(over.amount()).isEqualByComparingTo("25.00");
            assertThat(over.capped()).isTrue();
        }

        @Test
        @DisplayName("the uncapped figure survives so the cap is explainable")
        void keepsTheWorkings() {
            assertThat(calculator.assess(DUE, DUE.plusDays(60), new BigDecimal("25.00"))
                    .explain())
                    .contains("60 day(s) late")
                    .contains("capped at the replacement cost");
        }

        @Test
        @DisplayName("grace days are free, and only the excess is charged")
        void graceDaysAreNotCharged() {
            policy.setGraceDays(3);

            assertThat(calculator.assess(DUE, DUE.plusDays(3), REPLACEMENT).amount())
                    .isEqualByComparingTo("0.00");
            assertThat(calculator.assess(DUE, DUE.plusDays(5), REPLACEMENT).chargeableDays())
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("a book with no recorded cost falls back to the policy default")
        void fallsBackToDefaultCost() {
            policy.setDefaultReplacementCost(new BigDecimal("30.00"));

            assertThat(calculator.assess(DUE, DUE.plusDays(100), null).amount())
                    .isEqualByComparingTo("30.00");
        }
    }
}

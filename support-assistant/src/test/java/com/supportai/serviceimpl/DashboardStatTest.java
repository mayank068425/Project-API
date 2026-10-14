package com.supportai.serviceimpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardStatTest {

    @Test
    @DisplayName("the median resists a single very long outlier")
    void medianIsRobust() {
        // One incident left open over a weekend drags the mean far above anything
        // typical. Reporting only the mean invites the wrong conclusion about
        // whether response times are improving.
        List<Duration> durations = Arrays.asList(
                Duration.ofMinutes(10), Duration.ofMinutes(12), Duration.ofMinutes(15),
                Duration.ofMinutes(18), Duration.ofMinutes(4000));

        var stat = DashboardServiceImpl.stat(durations);

        assertThat(stat.medianMinutes()).isEqualTo(15);
        assertThat(stat.meanMinutes()).isGreaterThan(800);
        assertThat(stat.sample()).isEqualTo(5);
    }

    @Test
    @DisplayName("incidents without the timestamp are excluded, not counted as zero")
    void nullsAreExcluded() {
        // An incident that was never acknowledged has no acknowledgement time.
        // Treating that as instant would improve the figure by leaving out the
        // worst cases.
        var stat = DashboardServiceImpl.stat(Arrays.asList(
                Duration.ofMinutes(10), null, Duration.ofMinutes(20), null));

        assertThat(stat.sample()).isEqualTo(2);
        assertThat(stat.medianMinutes()).isEqualTo(10);
    }

    @Test
    @DisplayName("an empty sample reports null, not zero")
    void emptySampleIsHonest() {
        var stat = DashboardServiceImpl.stat(List.of());

        // Zero reads as "we acknowledge instantly" rather than "we have no data",
        // and those are very different dashboards.
        assertThat(stat.medianMinutes()).isNull();
        assertThat(stat.meanMinutes()).isNull();
        assertThat(stat.sample()).isZero();
    }

    @Test
    @DisplayName("p90 uses nearest rank and never invents a value")
    void percentileIsNearestRank() {
        List<Long> sorted = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

        assertThat(DashboardServiceImpl.percentile(sorted, 0.5)).isEqualTo(5L);
        assertThat(DashboardServiceImpl.percentile(sorted, 0.9)).isEqualTo(9L);
        assertThat(DashboardServiceImpl.percentile(sorted, 1.0)).isEqualTo(10L);
    }
}

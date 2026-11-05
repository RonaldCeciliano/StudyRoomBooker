package com.studyroombooker.backend;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.studyroombooker.backend.service.ShiftSplitter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftSplitterTests {
    @Test
    void splitsEveryQuarterHourDurationWithoutGapsOrOversizedShifts() {
        Instant start = Instant.parse("2030-01-01T00:00:00Z");
        for (int slots = 1; slots <= 96; slots++) {
            Instant end = start.plusSeconds(slots * 900L);
            var intervals = ShiftSplitter.split(start, end);
            assertThat(intervals).isEqualTo(ShiftSplitter.split(start, end));
            assertThat(intervals).hasSize((slots + 15) / 16);
            Instant cursor = start;
            long min = Long.MAX_VALUE;
            long max = 0;
            for (var interval : intervals) {
                assertThat(interval.startsAt()).isEqualTo(cursor);
                long seconds = Duration.between(cursor, interval.endsAt()).getSeconds();
                assertThat(seconds).isBetween(900L, 14400L);
                assertThat(seconds % 900).isZero();
                min = Math.min(min, seconds);
                max = Math.max(max, seconds);
                cursor = interval.endsAt();
            }
            assertThat(cursor).isEqualTo(end);
            assertThat(max - min).isLessThanOrEqualTo(900);
        }
    }

    @Test
    void followsArchitectureExamples() {
        assertThat(lengths(12)).containsExactly(240L, 240L, 240L);
        assertThat(lengths(9)).containsExactly(180L, 180L, 180L);
        assertThat(lengths(5)).containsExactly(150L, 150L);
    }

    private List<Long> lengths(int hours) {
        var start = Instant.parse("2030-01-01T00:00:00Z");
        return ShiftSplitter.split(start, start.plusSeconds(hours * 3600L)).stream()
            .map(interval -> Duration.between(interval.startsAt(), interval.endsAt()).toMinutes()).toList();
    }
}

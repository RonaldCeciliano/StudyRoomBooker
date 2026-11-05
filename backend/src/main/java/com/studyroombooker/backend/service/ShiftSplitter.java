package com.studyroombooker.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Deterministic, even allocation of quarter-hour slots; earlier shifts get remainders. */
public final class ShiftSplitter {
    private ShiftSplitter() {}

    public record Interval(Instant startsAt, Instant endsAt) {}

    public static List<Interval> split(Instant start, Instant end) {
        long seconds = Duration.between(start, end).getSeconds();
        if (start.getNano() != 0 || end.getNano() != 0 || start.getEpochSecond() % 900 != 0
                || end.getEpochSecond() % 900 != 0 || seconds <= 0) {
            throw new IllegalArgumentException("Expected a positive interval aligned to quarter hours");
        }
        long slots = seconds / 900;
        int count = Math.toIntExact((slots + 15) / 16);
        var result = new ArrayList<Interval>(count);
        Instant cursor = start;
        for (int i = 0; i < count; i++) {
            long length = slots / count + (i < slots % count ? 1 : 0);
            Instant next = cursor.plusSeconds(length * 900);
            result.add(new Interval(cursor, next));
            cursor = next;
        }
        return List.copyOf(result);
    }
}

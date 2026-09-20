package dev.xcloud.taskboard.domain;

import java.time.LocalDateTime;

/** Produces ISO-8601 timestamps stored as SQLite TEXT (native, round-trip safe). */
public final class LocalDateTimeFormatter {

    private LocalDateTimeFormatter() {
    }

    public static String now() {
        return LocalDateTime.now().toString();
    }
}
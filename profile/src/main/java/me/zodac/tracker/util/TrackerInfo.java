package me.zodac.tracker.util;

import org.apache.commons.csv.CSVRecord;

public record TrackerInfo(String name, String loginLink, String profilePage, String username, String password) {

    public static TrackerInfo fromCsv(final CSVRecord csvRecord) {
        return new TrackerInfo(
            csvRecord.get("name"),
            csvRecord.get("loginLink"),
            csvRecord.get("profilePage"),
            csvRecord.get("username"),
            csvRecord.get("password")
        );
    }
}

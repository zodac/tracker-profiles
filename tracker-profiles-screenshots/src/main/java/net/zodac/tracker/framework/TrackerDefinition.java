/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2025 zodac.net
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package net.zodac.tracker.framework;

import java.util.Locale;
import org.apache.commons.csv.CSVRecord;

/**
 * Simple class to hold the information for a given tracker.
 *
 * @param name        the tracker name
 * @param loginLink   the link to the tracker's login page
 * @param profilePage the link to the user's profile page on the tracker
 * @param username    the user's username
 * @param password    the user's password
 * @param manual      whether the tracker requires a manual interaction
 */
public record TrackerDefinition(String name, String loginLink, String profilePage, String username, String password, boolean manual)
    implements Comparable<TrackerDefinition> {

    /**
     * Converts a {@link CSVRecord} from {@link TrackerCsvReader} into a {@link TrackerDefinition} instance.
     *
     * @param csvRecord the {@link CSVRecord} holding a single tracker's information
     * @return the {@link TrackerDefinition}
     */
    public static TrackerDefinition fromCsv(final CSVRecord csvRecord) {
        return new TrackerDefinition(
            csvRecord.get("trackerName"),
            csvRecord.get("loginLink"),
            csvRecord.get("profilePage"),
            csvRecord.get("username"),
            csvRecord.get("password"),
            Boolean.parseBoolean(csvRecord.get("manual"))
        );
    }

    @Override
    public int compareTo(final TrackerDefinition other) {
        return name.toLowerCase(Locale.getDefault()).compareTo(other.name.toLowerCase(Locale.getDefault()));
    }
}

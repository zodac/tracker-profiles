-- The profile screenshots of the latest successful execution of each tracker (one per redaction type), shown on the Trackers tab and kept across
-- restarts. A tracker's screenshots are all replaced when it succeeds again
CREATE TABLE tracker_screenshots (
    name           TEXT    NOT NULL COLLATE NOCASE,
    redaction_type TEXT    NOT NULL,
    position       INTEGER NOT NULL,
    path           TEXT    NOT NULL,
    saved_at       INTEGER NOT NULL,
    PRIMARY KEY (name, redaction_type)
) STRICT, WITHOUT ROWID;

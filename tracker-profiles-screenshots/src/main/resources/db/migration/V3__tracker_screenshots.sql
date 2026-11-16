-- The latest screenshots of each tracker, shown on the Trackers tab and kept across restarts: the profile screenshots of its latest successful
-- execution (one per redaction type, with the redaction type as the ID), and the failure screenshot of its latest execution if it failed (with the ID
-- 'failure'). Its profile screenshots are replaced when it succeeds again, and its failure screenshot is replaced when it fails again
CREATE TABLE tracker_screenshots (
    name           TEXT    NOT NULL COLLATE NOCASE,
    id             TEXT    NOT NULL,
    position       INTEGER NOT NULL,
    path           TEXT    NOT NULL,
    saved_at       INTEGER NOT NULL,
    PRIMARY KEY (name, id)
) STRICT, WITHOUT ROWID;

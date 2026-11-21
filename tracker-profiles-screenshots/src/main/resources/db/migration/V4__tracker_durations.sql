-- The durations of the most recent successful executions of each tracker (excluding any time spent waiting for a user input), used to estimate how
-- long it takes. Only the latest few of each tracker are kept
CREATE TABLE tracker_durations (
    name        TEXT    NOT NULL COLLATE NOCASE,
    finished_at INTEGER NOT NULL,
    duration    INTEGER NOT NULL CHECK (duration >= 0),
    PRIMARY KEY (name, finished_at)
) STRICT, WITHOUT ROWID;

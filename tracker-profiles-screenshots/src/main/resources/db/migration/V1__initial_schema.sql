-- The schema created before the database was versioned. 'IF NOT EXISTS' lets a database created by those versions (whose tables already exist)
-- be baselined at version 0 and then migrated like a new one
CREATE TABLE IF NOT EXISTS tracker_credentials (
    name     TEXT PRIMARY KEY COLLATE NOCASE,
    username TEXT    NOT NULL DEFAULT '',
    password TEXT    NOT NULL DEFAULT '',
    enabled  INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS settings (
    name  TEXT PRIMARY KEY COLLATE NOCASE,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tracker_results (
    name        TEXT PRIMARY KEY COLLATE NOCASE,
    finished_at INTEGER NOT NULL,
    successful  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS metadata (
    name  TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

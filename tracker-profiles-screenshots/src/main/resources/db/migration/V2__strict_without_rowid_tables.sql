-- Rebuilds every table as STRICT (column types are enforced) and WITHOUT ROWID (rows are stored in primary key order, so a lookup or ordered
-- scan by name needs no separate index), with CHECK constraints on the boolean columns. SQLite cannot alter a table in place, so each table is
-- copied into a new table that replaces it

CREATE TABLE tracker_credentials_new (
    name     TEXT    NOT NULL PRIMARY KEY COLLATE NOCASE,
    username TEXT    NOT NULL DEFAULT '',
    password TEXT    NOT NULL DEFAULT '',
    enabled  INTEGER NOT NULL DEFAULT 0 CHECK (enabled IN (0, 1))
) STRICT, WITHOUT ROWID;
INSERT INTO tracker_credentials_new (name, username, password, enabled)
SELECT name, username, password, enabled FROM tracker_credentials;
DROP TABLE tracker_credentials;
ALTER TABLE tracker_credentials_new RENAME TO tracker_credentials;

CREATE TABLE settings_new (
    name  TEXT NOT NULL PRIMARY KEY COLLATE NOCASE,
    value TEXT NOT NULL
) STRICT, WITHOUT ROWID;
INSERT INTO settings_new (name, value)
SELECT name, value FROM settings;
DROP TABLE settings;
ALTER TABLE settings_new RENAME TO settings;

CREATE TABLE tracker_results_new (
    name        TEXT    NOT NULL PRIMARY KEY COLLATE NOCASE,
    finished_at INTEGER NOT NULL,
    successful  INTEGER NOT NULL CHECK (successful IN (0, 1))
) STRICT, WITHOUT ROWID;
INSERT INTO tracker_results_new (name, finished_at, successful)
SELECT name, finished_at, successful FROM tracker_results;
DROP TABLE tracker_results;
ALTER TABLE tracker_results_new RENAME TO tracker_results;

CREATE TABLE metadata_new (
    name  TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
) STRICT, WITHOUT ROWID;
INSERT INTO metadata_new (name, value)
SELECT name, value FROM metadata;
DROP TABLE metadata;
ALTER TABLE metadata_new RENAME TO metadata;

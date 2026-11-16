## Web UI

The application is now run entirely through a web UI (on port `8080`, set with `WEB_UI_PORT`), instead of executing once from the command line and
exiting. The container keeps running, and each execution is started from the browser:

- **Main**: start or cancel an execution and follow its progress. Each tracker is listed with its current step, attempt and duration, and can be
  expanded to see its own logs, grouped by step. The run options you are most likely to change (tracker order, attempts, log level, etc.) are shown
  here, and are locked while an execution is in progress. An expanded tracker shows thumbnails of its screenshots (or its failure screenshot), which
  open in a gallery to move between the redaction types. Each screenshot can be downloaded, as can all screenshots of a tracker, or the latest
  screenshots of every tracker that has succeeded in any execution (as a `.zip` file, while they are still on disk). After an execution in which some
  trackers failed, **Failures only** executes just those trackers again
- **Trackers**: the credentials of every supported tracker, grouped as enabled, disabled or unset, with the result of its most recent execution and
  its latest screenshots (those of its latest success, and of its latest failure; kept across restarts, shown by expanding its row). Credentials can
  be imported from a CSV file, and several trackers can be selected (`Ctrl`+click, or `Ctrl`+`Shift`+click for a range) to enable, disable or remove
  them together
- **Settings**: every other option, preloaded from the environment variables. A value saved in the web UI overrides the environment variable,
  including after a restart

Manual trackers now ask for input (continue, skip, or wait 5 more minutes) in the web UI, rather than in a Swing dialog. The browser window itself
still needs `DISPLAY` to be configured.

Tracker credentials, settings and the latest results are stored in a SQLite database in the `/app/data` volume, so mount a volume to keep them
between containers (the [example compose file](docs/docker-compose.example.yml) uses the `tracker-profiles-data` volume). Passwords are encrypted, and are never sent back to the browser.

## Backwards Incompatible Changes

- `SECRET_KEY` is a new **required** environment variable, used to encrypt the stored passwords. It must be at least 16 characters (for example,
  the output of `openssl rand -base64 32`), and must stay the same for as long as the database is used
- The trackers are no longer read from a CSV file on each execution: import the CSV file once on the **Trackers** tab instead (the format is
  unchanged)
- `BROWSER_WIDTH` and `BROWSER_HEIGHT` are replaced by `BROWSER_RESOLUTION` (e.g. `1680x1050`)
- `INPUT_TIMEOUT_ENABLED` is removed, as `INPUT_TIMEOUT_SECONDS=0` now disables the timeout
- The following options are removed, along with the console progress bar:
    - `CSV_COMMENT_SYMBOL`
    - `FAIL_ON_UNSUPPORTED_TRACKER`
    - `LOG_TRACKER_NAME`
    - `PROGRESS_BAR_COMPLETE_CHARACTER`
    - `PROGRESS_BAR_ENABLED`
    - `PROGRESS_BAR_FORMAT`
    - `PROGRESS_BAR_INCOMPLETE_CHARACTER`
    - `PROGRESS_BAR_LENGTH`
    - `TRACKER_INPUT_FILE_PATH`
- New options: `DATABASE_PATH`, `SECRET_KEY` and `WEB_UI_PORT`

## Docker Compose

The application is now run with Docker Compose rather than `docker run`. See
[docker-compose.example.yml](docs/docker-compose.example.yml) for an example, and the [README](README.md#running-with-docker-compose) for how to use
it. The example runs a hardened container: a non-root user with no capabilities, a read-only root filesystem, memory and process limits, and the web
UI only reachable from the host (`127.0.0.1`).

The container is now stopped gracefully by `docker stop` (`SIGTERM`), as well as `SIGINT`.

---
name: run-app
description: Builds and starts the web UI locally (no Docker) and takes full-page screenshots of its tabs, to review a UI change. Use after any change to the web UI, or when asked to run, render or screenshot the app.
---

# Run the app and screenshot the web UI

The script builds the JAR, starts the application on a fresh temporary database, waits for the web UI, saves one full-page screenshot per tab
(with headless `chromium`), and stops the application when it exits:

```bash
S=<scratchpad directory>/ui-review
.claude/skills/run-app/screenshot.sh "$S" main settings
```

- Tabs are `main`, `trackers` and `settings` (opened by the URL hash), and default to all three.
- Environment variables are passed through to the application, so set any option to a non-default value to show the state being reviewed (for
  example `NUMBER_OF_PARALLEL_THREADS=3 LOG_LEVEL=DEBUG REDACTION_TYPE=BLUR,BOX`). `DATABASE_PATH`, `SECRET_KEY`, `WEB_UI_PORT` (default
  `8099`) and `OUTPUT_DIRECTORY_PARENT_PATH` are set by the script, since their defaults point at `/app`.
- `SKIP_BUILD=true` reuses the JAR in `tracker-profiles-screenshots/target` (only when nothing changed since the last build: the web resources
  are served from the JAR). `WINDOW_SIZE` (default `1400,2200`) sets the viewport, so a wider or narrower layout can be checked.
- The database is empty, so the Trackers tab lists every supported tracker as unset. To show enabled or disabled trackers, keep the
  application running (copy the launch part of the script) and import credentials through `POST /api/trackers/import` (see
  `TrackerApiHandler`) before the screenshot.
- Logs are in `$S/run/application.log` (and the build output in `$S/run/build.log`).

## Then

1. **Look at every screenshot** with the Read tool. A blank or half-loaded page means the UI failed to start: read the log.
2. Check the change is visible and that nothing around it broke.
3. Send the screenshots to the user with `SendUserFile` (`display: render`), with a caption saying what to look at.

## Pitfalls

- Do not find the application with `pgrep -f`/`pkill -f` on the JAR name: the pattern also matches the shell running the command. The script
  stops the application by its PID.
- A screenshot only shows the page as loaded. A state that needs a click (an expanded tracker, an open dialog) needs a browser driven through CDP
  (`chromium --remote-debugging-port`), which is not set up here.

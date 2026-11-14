# CLAUDE.md

@../ai-sandbox/rules/CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build, Test, and Lint Commands

Maven profiles are activated by passing a property flag, not `-P`:

```bash
# Build JAR only (no tests, no lints)
mvn clean install

# Run everything (tests + lints)
mvn clean install -Dall
```

To run a single test class:

```bash
mvn test -Dtests -pl tracker-profiles-screenshots -Dtest=TextSearcherTest
```

## Architecture Overview

### Execution Flow

The application is used only through its web UI (there is no CLI mode). Each execution is started from the browser, and for each enabled tracker:
opens a browser, logs in, navigates to the user profile page, redacts sensitive information, takes a full-page screenshot, and logs out.

Entry point: `ApplicationLauncher` (starts the web UI, see [Web UI](#web-ui)) → `RunManager.start()` (loads the enabled trackers from the database) →
`TrackerRetriever.groupByType` → `DisplayValidator` (AWT/display check) → `ScreenshotOrchestrator` → `ProfileScreenshotExecutor` (per tracker) →
`AbstractTrackerHandler` subclass.

With the system property `tracker-profiles.appcds-training=true`, `ApplicationLauncher` starts and immediately stops the web UI; this is only used by
`docker/Dockerfile` to generate the AppCDS archive.

### Web UI

Lives in `net.zodac.tracker.web`. Uses the JDK `com.sun.net.httpserver.HttpServer` (module `jdk.httpserver`, included in the `jlink` list in
`docker/Dockerfile`) with virtual threads, Gson for JSON, and no web framework.

- **`WebServer`** — opens the database, removes saved settings for options that no longer exist, and registers the handlers.
- **`web.http`** — one handler per context: `RunApiHandler` (`/api/run`), `LogApiHandler` (`/api/logs?tracker={name}&after={id}`),
  `TrackerApiHandler` (`/api/trackers`, including `POST /api/trackers/import` for CSV content read by `TrackerCsvReader`), `SettingsApiHandler`
  (`/api/settings`), `ScreenshotApiHandler` (`/api/screenshots`, see [Screenshots](#screenshots)), `StaticResourceHandler` (`/`). API handlers
  extend `ApiHandler`, which requires `Content-Type: application/json` on `POST`/`PUT` (CSRF protection) and maps `HttpRequestException` to error
  responses.
- **`web.storage.ApplicationDatabase`** — SQLite (`sqlite-jdbc`), holding tracker credentials, settings overrides, and the result of each tracker's
  most recent execution (shown on the Trackers tab). Passwords are never returned by the API; a `null` password on save keeps the stored one, and an
  empty password removes it (the UI shows a saved password as a placeholder value, `SAVED_PASSWORD_VALUE` in `app.js`). Passwords are encrypted by
  `PasswordCipher` (AES-256-GCM, tracker name as associated data) with a key derived (PBKDF2) from the required `SECRET_KEY` env var and a salt stored
  in the `metadata` table. `ApplicationDatabase.open` encrypts any plain-text passwords, and fails if a stored password cannot be decrypted (the
  secret changed). `SECRET_KEY` is `ConfigurationOption.sensitive()`: never returned by the API (masked in `SettingView`) or logged.
- **`RunManager`** — runs one execution at a time on a background thread. Before each run it calls `Configuration.reload(...)` with the
  overrides saved in the database, so settings take effect without a restart. Implements `ExecutionListener` to track the progress of every tracker
  (`TrackerState`: waiting, running, succeeded, failed or cancelled, with its attempt and completed `TrackerStep`s), which `RunStatus` returns to the
  UI along with any pending user inputs.
- **No setting changes during an execution** — `SettingsService` rejects every save or reset with `IllegalStateException` (HTTP 409) while
  `RunManager.isExecutionInProgress()`, and the web UI locks every setting, so an execution always uses the configuration loaded when it started.
  After a save, `SettingsService` notifies its `SavedSettingsListener` (`RunManager.applySavedSettings`), which reloads the configuration.
- **`SettingsService`** — resolves each setting as saved value → environment variable → default, and validates all values together by calling
  `ApplicationConfiguration.load(overrides)` before saving. A saved value is kept (across restarts) even if it equals the environment variable or
  default (the API can reset it, but the web UI has no reset). `RunManager.reloadConfiguration` only applies saved values for options that are
  `editableAtRuntime` (not `Scope.STARTUP`).
- **User input** — manual trackers call `displayUtils().confirm(trackerName, message)`, which waits in `framework.input.UserInputRequests` until
  the user responds in the web UI (`POST /api/run/input/{id}/continue|skip|extend`), the page in the browser changes, or `INPUT_TIMEOUT_SECONDS`
  passes (read while waiting, so a change applies to a waiting request). There is no Swing dialog; the browser window itself still needs `DISPLAY`.
- **`web.log.WebLogAppender`** — a Log4j2 appender installed programmatically, keeping the log messages of the current execution separately for
  each tracker (from the `tracker` key of the `ThreadContext`, since trackers run in parallel), plus an execution log for messages not logged by a
  tracker, and each tracker's latest `INFO`+ message for `RunStatus` (cleared by `RunManager` when a step completes or an attempt starts). Each
  `LogEntry` also carries the attempt, `TrackerStep` and section it was logged in (see [Logging](#logging)). It is cleared when an execution starts.
  The UI fetches a tracker's logs only while it is expanded, polling with the ID of the last message it has, and groups them by step (a phase),
  with a divider per attempt once a tracker is retried and a heading per section. `RunStatus` gives each completed step's time for the phase
  durations.

#### Screenshots

`ProfileScreenshotExecutor` reports each saved screenshot to the `ExecutionListener` (`trackerScreenshotSaved`, also for an existing screenshot that
is skipped, and `trackerFailureScreenshotSaved`). `RunManager` keeps the latest one per `RedactionType` (a retry replaces it) and the latest failure
screenshot (dropped when the tracker succeeds, as the file is deleted), and lists them in each `RunStatus.TrackerProgress`: a succeeded tracker
shows its profile screenshots, a failed or cancelled one only its failure screenshot, and a running one both. Only the current (or most recent)
execution is kept, in memory.

`ScreenshotApiHandler` only serves files listed by `RunManager` (by tracker name and screenshot ID, never a path from the request):
`?tracker=X&id=Y` is a single PNG (`&download=true` as an attachment). `&thumbnail=true` is a JPEG thumbnail of the top of the page, and
`&preview=true` (used by the gallery) the whole page scaled to 1920 wide (at most 10800 tall), or the PNG itself if it is not wider. Both come
from `web.screenshot.ScreenshotPreviews`, which never decodes a whole screenshot (an 8K full-page PNG needs over 600MB, more than the default
512MB heap): it decodes every `n`th pixel (keeping twice the target width, then scaling smoothly) in strips of at most 64MB, one image at a time,
and caches up to 64MB of images by path, last-modified time and kind. `?tracker=X` downloads all of a
tracker's screenshots, and no parameters downloads the profile screenshots of every succeeded tracker. A download of one screenshot is the PNG
itself, and several are a zip (`ScreenshotArchive`, failure screenshots under `errors/`) named `tracker-profiles_v<version>_<yyyy-MM-dd_HH:mm:ss>`
from the execution start in `TIMEZONE`. The version is the `Implementation-Version` of the JAR manifest (set by the shade plugin), read by
`ApplicationVersion`, and `unknown` outside the JAR. The UI adds each screenshot's `savedAt` to its image URLs, so images can be cached by the
browser and a replaced screenshot is fetched again.

In the UI, the screenshots are shown in each expanded tracker above its logs (re-rendered only when they change), and open in a `<dialog>` gallery.

#### Cancelling an Execution

`POST /api/run/cancel` calls `RunManager.cancel()`, which stops the execution without stopping the application:

1. `ExecutionCancellation.requestCancellation()` sets a global flag. It is checked at checkpoints so no new work starts: before each tracker
   type (`ScreenshotOrchestrator`), each tracker, each retry attempt (`ProfileScreenshotExecutor`), while waiting for a user input
   (`UserInputRequests`), and whenever a driver is acquired or created (`DriverPool` — including no replacement for a dead pooled driver).
   Checkpoints throw `ExecutionCancelledException`.
2. `DriverPool.terminateAll()` kills every browser process descended from the JVM (`chromedriver`, Chrome, `selenium-manager`), including
   browsers still starting up, so in-flight Selenium calls fail immediately. It then runs `shutdown()` for bookkeeping.
3. The execution thread is interrupted, which cancels the worker tasks (unblocking any waiting on a pooled driver).

While cancelled, failures are expected and logged at `DEBUG` (not `WARN`), and trackers that fail or never start are not recorded as results.
The state is `CANCELLING` until the execution thread ends, then `CANCELLED`; a new execution cannot start until then, and
`ExecutionCancellation.reset()` is called when it does. When adding new long-running work or browser creation, add a checkpoint, and never
create a driver without registering it in `DriverPool` (or checking `ExecutionCancellation` after creating it).

The frontend is plain HTML/CSS/JS in `src/main/resources/web/` (served with `Content-Security-Policy: default-src 'self'`, so no inline scripts or
styles and no CDNs). `app.js` and `theme.js` are linted by ESLint with the shared config, like the redaction scripts.

- The Main tab lists the trackers of the execution grouped by state (rows are kept between status updates and moved between groups, so an expanded
  tracker stays expanded), and shows the options with `Tab.MAIN` in a collapsible "Run options" section (open until Start is pressed). The tracker
  list and execution log are hidden until an execution has started. While an execution is running, every option (on both tabs) is locked. The Settings
  tab shows the `Tab.SETTINGS` options, with `Scope.STARTUP` options read-only and grouped last. `MULTI_SELECT` options are a list dragged into order
  (or moved with the arrow keys on the handle), each with a switch to include it; `INPUT_TIMEOUT_SECONDS` is a switch (off saves `0`) with a seconds
  field.
- The Trackers tab groups trackers as enabled (headless/manual), disabled, and unset (no username or password, with the last run, enable and
  remove columns hidden). Rows are kept between renders and only moved rows change place, and when a tracker moves the page is scrolled to keep the
  rows around it still (the page never follows the moved row). Ctrl/Cmd+click selects trackers (Ctrl/Cmd+Shift+click a range), which the selection
  bar enables, disables or removes together. A tracker whose credentials change its group while it is being edited is pinned
  in its group until the user clicks outside its row, or makes no change for 10s.
- There are no save buttons: every change is saved automatically and confirmed with a toast. Text inputs save after a 1s pause in typing (or
  on blur/Enter), switches and selects save immediately. Saves are queued so they reach the server in order, and rows are updated in place
  (not re-rendered) so the focused input is never replaced.
- The theme is light/dark, following the OS until the user picks one with the header toggle. The choice is stored in `localStorage`, and
  `theme.js` (a blocking script in `<head>`) applies it before the first paint. Dark colours are defined twice in `style.css`, once for
  `[data-theme="dark"]` and once for the OS preference when the user has not chosen light; keep the two blocks in sync.

### Retry and Screenshot Execution

`ProfileScreenshotExecutor` has three layers:

- **`takeScreenshot`** (package-private) — the entry point called by `ScreenshotOrchestrator`. Sets up per-tracker logging context
  and delegates to the retry loop.
- **`takeScreenshotWithAttempts`** (private) — the retry loop. Calls `screenshotTracker` up to `numberOfScreenshotAttempts` times
  and returns success only when every `TrackerStep` has been recorded.
- **`screenshotTracker`** (private) — a single attempt. Resolves the handler via `TrackerHandlerFactory`, delegates to
  `screenshotProfile`, and contains the full per-exception catch chain with tailored log messages. Caught failures fall through
  to a fresh retry; uncaught throws propagate out.

Failure-screenshot writes go to `ScreenshotDirectories.errors()`. That directory is created up-front by `ScreenshotDirectories.createAll()` when
`TAKE_SCREENSHOT_ON_ERROR=true`, and otherwise by `ScreenshotDirectories.createErrorsDirectory()` (under a lock, since trackers run in parallel) before
a failure screenshot, as the option can be enabled during an execution.

### Tracker Handler Pattern

All tracker implementations live in `handler/` and extend `AbstractTrackerHandler`. The base class implements the full screenshot workflow as a
template method; subclasses override selectors and optional hooks.

Handlers are discovered at startup via reflection: `TrackerHandlerFactory` scans the `net.zodac.tracker.handler` package for classes annotated with
`@TrackerHandler`, then instantiates them on demand by name match.

To add a new tracker:

1. Determine whether the tracker needs **any** method overrides:
    - **No overrides needed** (pure platform default) → add a `@TrackerHandler(name = "...", url = "...")` annotation directly to
      the base handler class (`Unit3dHandler`, `GazelleHandler`, etc.) in alphabetical order. **Do not create a new class.**
    - **At least one override needed** → create a new class in `handler/` extending the appropriate base class, annotate it with
      `@TrackerHandler`, and override only the methods that differ.
2. Annotate with `@TrackerHandler(name = "...", type = TrackerType.HEADLESS, url = "https://...")` (omit `type` for HEADLESS)
3. Override the required selectors — at minimum: `usernameFieldSelector()`, `passwordFieldSelector()`,
   `logoutButtonSelector()`, `profileLinkSelector()`
4. Opt into redaction by **implementing the relevant `Redaction` sub-interfaces** from `net.zodac.tracker.handler.redaction`
   (`RedactsEmail`, `RedactsIpAddress`, `RedactsTorrentPasskey`, `RedactsIrcPasskey`, `RedactsSensitiveInformation`)
   — each declares one selector method (e.g. `emailElements()`). Also implement structural interfaces as needed
   (`HasFixedHeader`, `HasFixedSidebar`, `HasDismissibleElement`, etc.), and any manual-interaction hooks
   (`preLoginClickAction()`, `postLoginClickAction()`).
5. Add the tracker's credentials to `./trackers_example.csv` and details to `README.md`

Use `XpathBuilder` (with `HtmlElement` constants and `XpathAttributePredicate`) to construct selectors rather than raw XPath strings.

Overrides of `TrackerTimings` methods (e.g. `pageTransitionsDuration()`, `pageLoadDuration()`) must be placed at the **bottom** of the
implementation class, after all other `@Override` methods and private helpers.

### Tracker Types

Two types control how the browser is created and whether user interaction is needed:

- **`HEADLESS`** — Runs Chrome without a UI. Default for most trackers.
- **`MANUAL`** — Runs Chrome with a UI; pauses execution via `displayUtils().confirm(...)` for captcha or 2FA entry, which the user answers in the
  web UI.
    - Override `preLoginClickAction()` or `postLoginClickAction()` in the handler.

Default execution order: `HEADLESS` → `MANUAL`. Configurable via `TRACKER_EXECUTION_ORDER`, including during an execution for types not yet started
(so `TrackerRetriever.groupByType` keeps every type, and `ScreenshotOrchestrator` only executes those in the order).

### Cloudflare Verification

A handler whose login page has a Cloudflare verification check (a full-page challenge, or a Turnstile widget in the login form) implements
`HasCloudflareCheck`. The check is passed automatically by `CloudflareSolver` (`net.zodac.tracker.framework.cloudflare`) in both headless and UI
browsers, so a Cloudflare check alone is **not** a reason to make a tracker `MANUAL`.

`CloudflareSolver` must never inspect the challenge with JavaScript executed in the page (`driver.executeScript`, or `findElement` on the
challenge elements) — Cloudflare detects this and fails the check. It locates the challenge through the CDP `DOM` domain, reads the result from
an isolated world, and clicks with CDP `Input.dispatchMouseEvent`. Headless browsers also need the `HeadlessChrome` marker removed from the user
agent (done in `JavaWebDriverFactory`), otherwise the check always fails.

Stricter websites also reject the click unless the `cdc_` variables that `chromedriver` injects into every page are removed. This is done by the
`perl` step in `docker/Dockerfile`, which blanks that code in the `chromedriver` binary (and fails the build if it cannot find it). Public
Cloudflare demo pages pass without it, so always verify changes against real tracker login pages.

The challenge announces its progress with `postMessage` events (`interactiveBegin`, `interactiveEnd`, `complete`, `fail`), which the solver records
from the isolated world. A challenge may pass with no click at all; it must never be clicked after `fail`, as the checkbox is then replaced by a
"Troubleshoot" link.

If the check cannot be passed automatically, a headless tracker throws `CloudflareVerificationException` (the attempt fails and may be retried),
while a UI browser falls back to asking the user (in the web UI), highlighting `cloudflareSelector()`.

### Counting Tracker Handlers

Run `grep -R --include='*.java' '^@TrackerHandler(' . | cut -d '"' -f2 | sort -u | wc -l`

### Configuration

All runtime configuration is loaded from environment variables in `ApplicationConfiguration`. `Configuration.init()` is called once at startup by
`ApplicationLauncher`; all subsequent callers use `Configuration.get()`. Any invalid value throws `IllegalArgumentException` at startup.

**No class may cache the configuration in a static field** (e.g. `private static final ApplicationConfiguration CONFIG = Configuration.get()`,
or a constant derived from it). The web UI calls `Configuration.reload(...)` before each execution, so a cached value would be stale. For handler
classes it is also a startup failure: `TrackerHandlerFactory` loads every handler class via reflection before `Configuration.init()` has been called,
so the static field would throw `IllegalStateException`. Call `Configuration.get()` inline at the point of use instead:

```java
// Correct — inline call inside the method body
@Override
protected By usernameFieldSelector() {
    if (Configuration.get().enableTranslationToEnglish()) {
        ...
    }
    return super.usernameFieldSelector();
}

// Wrong — static field triggers Configuration.get() at class-load time
private static final ApplicationConfiguration CONFIG = Configuration.get();
```

Every env var is defined once in the `ConfigurationOption` enum (web UI `Tab`, group, label, default value, `OptionType`, allowed values,
description, and `Scope`: `STARTUP` (environment only) or `EXECUTION` (read when an execution starts)). This
drives both loading and the web UI. Values are resolved as: override (saved in the web
UI) → environment variable → `ConfigurationOption.defaultValue()`, via the private `Source` record in `ApplicationConfiguration`.

New env vars must be:

1. Added to `ConfigurationOption` (alphabetical order). Use `Tab.MAIN` for options likely to be changed for an execution, otherwise
   `Tab.SETTINGS`. Use `Scope.STARTUP` if the value is only read at startup (a web UI change would have no effect), otherwise `Scope.EXECUTION`.
   A secret value must be marked in `ConfigurationOption.sensitive()`, and masked in `ApplicationConfiguration.print()` and `toString()`
2. Added as a record component (alphabetical order among components)
3. Loaded via a `getXxx(source)` private static method using `source.get(ConfigurationOption.XXX)` and, where needed,
   `parseIntegerInRange(input, envVarName, min, max)` (use `Integer.MAX_VALUE` for unbounded positive integers)
4. Added to the `load()` constructor call and `print()` debug log
5. Documented in the README `### Configuration Options` section, in the `Run Options` table for `Tab.MAIN`, the `Settings` table for other editable
   options, otherwise the `Startup-Only Options` table, and in all Docker run examples (Debian, Windows, development and performance analysis
   blocks), all in alphabetical order

### Redaction

Redaction is opt-in per category, via sub-interfaces of `Redaction` in `net.zodac.tracker.handler.redaction`:

| Interface                     | Selector method (override)        | Category               |
|-------------------------------|-----------------------------------|------------------------|
| `RedactsEmail`                | `emailElements()`                 | Email addresses        |
| `RedactsIpAddress`            | `ipAddressElements()`             | IP addresses           |
| `RedactsTorrentPasskey`       | `torrentPasskeyElements()`        | Torrent passkey/RSS    |
| `RedactsIrcPasskey`           | `ircPasskeyElements()`            | IRC passkey            |
| `RedactsSensitiveInformation` | `sensitiveElements()` (Map)       | Anything else          |

A handler implements only the interfaces it needs (e.g. `implements Unit3dHandler, RedactsEmail, RedactsIpAddress`). Each interface
provides a default `redactXxxElements(driver, redactor)` method that consumes the handler's selectors and dispatches to the
`Redactor` — no logic in the handler beyond returning the selectors.

If `RedactionType` is not `NONE`, `AbstractTrackerHandler.redactElements()` delegates to `RedactionRegistry.redactAll(...)`
(`net.zodac.tracker.framework.redaction.RedactionRegistry`), which iterates every registered `Redaction` interface and invokes
the matching default method on the handler. The `RedactionType` selects the visual treatment — `BOX` overlay or Gaussian `BLUR`
(`NONE` skips the redaction phase). Redaction runs after profile page load and before the screenshot.

To add a new redaction category: create a new interface in `handler/redaction/` extending `Redaction`, then add a single
`RedactionEntry` to `RedactionRegistry.ENTRIES`. No changes required in `AbstractTrackerHandler`.

## Logging

### Log Levels

Log4j2 is used throughout. Apply levels as follows:

- **`ERROR`** — any error that ends execution entirely
- **`WARN`** — unexpected error that allows the next tracker to proceed
- **`INFO`** — user-facing status updates on overall execution progress
- **`DEBUG`** — detail on each discrete step (clicking buttons, filling fields, navigating pages)
- **`TRACE`** — fine-grained detail on waits, timeouts, and low-level state changes

### Exclusions

`logging.properties` is used to suppress irrelevant Selenium log entries.

### Progress

Progress is reported to the web UI through `ExecutionListener` (implemented by `RunManager`): `executionStarted`, `trackerTypeStarted`,
`trackerStarted`, `trackerStepCompleted` (once per `TrackerStep`, the first time it succeeds, even across retries) and `trackerCompleted`.

`TrackerLogContext` sets the Log4j `ThreadContext` of a tracker's thread: its name (key `tracker`, which the console pattern prints as a prefix), the
attempt, the `TrackerStep` in progress, and an optional section of the step (the redaction type while taking screenshots). `ProfileScreenshotExecutor`
calls `TrackerLogContext.startStep(...)` as each step begins; messages logged before the first step are part of `OPEN_TRACKER`. The web UI uses
these to filter and group the logs, so log messages must not:

- include the tracker name
- start with indentation, `-`, `>>>` or other list markers (the UI groups messages instead)
- only announce the step that is starting (such as "Opening tracker"), since the UI already shows it
- be blank lines used as separators

Never write directly to `System.out` or `System.err`; use the logger.

## Coding Standards

### String Formatting

Always use `"".formatted()` — never `String.format()`:

```java
// Correct
"Hello, %s!".formatted(name)

// Wrong
String.format("Hello, %s!", name)
```

For multi-line strings, use a text block with `.formatted()`. Do **not** use a text block for a single-line string —
SpotBugs flags the trailing `\n` as `VA_FORMAT_STRING_USES_NEWLINE` when `.formatted()` is used:

```java
// Multi-line — text block is correct
"""
<div class="%s">
  <span>%s</span>
</div>
""".formatted(cssClass, content)

// Single-line — plain string, not a text block
"<div class=\"%s\">%s</div>".formatted(cssClass, content)
```

### Instantiation: Private Constructor + Static Factory

Classes that are explicitly instantiated must use a **private constructor** and a **`static` factory method** instead of a public constructor. This
applies to all non-abstract, non-utility classes, unless a private constructor is impossible (e.g., the canonical constructor of a public `record`
cannot be private).

The private constructor must only assign fields — no validation, no logic. All input validation and exceptions belong in the static factory method.
Other static factories (e.g. `ofDefault()`) must call the validating factory, not the private constructor directly.

```java
// Correct
public final class TrackerCredential {

    private final String username;
    private final String password;

    private TrackerCredential(final String username, final String password) {
        this.username = username;  // assignments only
        this.password = password;
    }

    public static TrackerCredential of(final String username, final String password) {
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        return new TrackerCredential(username, password);  // new only called here
    }

    public static TrackerCredential ofDefault() {
        return of("default", "");  // delegates to of(), not new TrackerCredential()
    }
}

// Wrong — public constructor, and validation in constructor
public final class TrackerCredential {
    public TrackerCredential(final String username, final String password) {
        if (username.isBlank()) { throw new IllegalArgumentException(...); }
        ...
    }
}
```

Name the factory method `of(...)` for value-type objects. Use a more descriptive verb (`create`, `from`, `forTracker`, etc.) when the method does
non-trivial work beyond field assignment.

### Linting

Shared linter configs come from the `code-quality-config` git submodule (run `git submodule update --init` after cloning), and are
wired into Maven by the `net.zodac:parent-pom` parent:

- `code-quality-config/java/` — Checkstyle, PMD, SpotBugs include/exclude filters, license header definition
- `code-quality-config/docker/.hadolint.yaml`
- `code-quality-config/javascript/eslint.config.cjs`
- `code-quality-config/markdown/.markdownlint.json`

Do not edit files inside the submodule. Project-specific overrides live in `code-quality-config-overrides/` (currently only
`checkstyle-suppression.xml`, layered on top of the shared suppressions).

Suppress only when genuinely necessary. The codebase has very few suppressions — keep it that way.

Markdown files follow `code-quality-config/markdown/.markdownlint.json`: body lines max 150 characters, heading lines max 120 characters, code
block lines max 200 characters (tables are exempt). Always run the IDE formatter (Ctrl+Shift+F) on any `.md` file after editing.

### Concurrency

Use `ReentrantLock` over `synchronized` blocks (PMD `AvoidSynchronizedStatement`):

```java
// Correct
private final Lock lock = new ReentrantLock();

lock.lock();
try {
    // critical section
} finally {
    lock.unlock();
}

// Wrong
synchronized (this) {
    // critical section
}
```

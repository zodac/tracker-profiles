# CLAUDE.md

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

The application reads a CSV of tracker credentials, then for each tracker: opens a browser, logs in, navigates to the user profile page, redacts
sensitive information, takes a full-page screenshot, and logs out.

Entry point: `ApplicationLauncher` → `TrackerRetriever` (load CSV) → `DisplayValidator` (AWT/display check) → `ScreenshotOrchestrator` →
`ProfileScreenshotExecutor` (per tracker) → `AbstractTrackerHandler` subclass.

### Retry and Screenshot Execution

`ProfileScreenshotExecutor` has three layers:

- **`takeScreenshot`** (package-private) — the entry point called by `ScreenshotOrchestrator`. Sets up per-tracker logging context
  and delegates to the retry loop.
- **`takeScreenshotWithAttempts`** (private) — the retry loop. Calls `screenshotTracker` up to `numberOfScreenshotAttempts` times
  and returns success only when every `TrackerStep` has been recorded.
- **`screenshotTracker`** (private) — a single attempt. Resolves the handler via `TrackerHandlerFactory`, delegates to
  `screenshotProfile`, and contains the full per-exception catch chain with tailored log messages. Caught failures fall through
  to a fresh retry; uncaught throws propagate out.

Failure-screenshot writes go to `ScreenshotDirectories.ERRORS` / `ScreenshotDirectories.REDACTION_ERRORS`. Those directories
are created up-front (and only when `TAKE_SCREENSHOT_ON_ERROR=true`) by `ScreenshotDirectories.createAll()` so the parallel
write path never has to create them on demand.

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
- **`MANUAL`** — Runs Chrome with a UI; pauses execution via `DisplayUtils.userInputConfirmation()` for captcha or 2FA entry.
    - Override `preLoginClickAction()` or `postLoginClickAction()` in the handler.

Default execution order: `HEADLESS` → `MANUAL`. Configurable via `TRACKER_EXECUTION_ORDER` env var.

### Counting Tracker Handlers

Run `grep -R --include='*.java' '^@TrackerHandler(' . | cut -d '"' -f2 | sort -u | wc -l`

### Configuration

All runtime configuration is loaded from environment variables in `ApplicationConfiguration`. `Configuration.init()` is called once at startup by
`ApplicationLauncher`; all subsequent callers use `Configuration.get()`. Any invalid value throws `IllegalArgumentException` at startup.

**`AbstractTrackerHandler` subclasses must never declare a `private static final ApplicationConfiguration CONFIG = Configuration.get()` field.**
`TrackerHandlerFactory` loads every handler class via reflection at startup, before `Configuration.init()` has been called. A static field on any
handler class would therefore call `Configuration.get()` before initialisation, throwing `IllegalStateException`. If a handler method needs
configuration, call `Configuration.get()` inline at the point of use instead:

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

New env vars must be:

1. Added as a record component (alphabetical order among components)
2. Loaded via a `getXxx()` private static method using `getOrDefault()` or
   `parseIntegerInRange(input, envVarName, min, max)` (use `Integer.MAX_VALUE` for unbounded positive integers)
3. Added to the `load()` constructor call and `print()` debug log
4. Documented in the README `### Configuration Options` table and both Docker run examples (Debian and Windows blocks), all in alphabetical order

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

### Progress Bar

The progress bar is managed by `ProgressBarManager` and kept pinned to the bottom of the console by `ProgressBarPrintStream`, which replaces
`System.out` at startup and intercepts every write. Any new code that produces console output must go through the standard logger — writing directly
to `System.out` or `System.err` will corrupt the bar rendering.

The Clique bar is driven by exactly multiple ticks per tracker (see `TrackerStep` for values). A separate tracker counter (`X/Y`) is appended by
`ProgressBarManager.getProgressBarContent()`. Call `tick()` at each of the tracker steps inside `ProfileScreenshotExecutor`, and `tickTracker()` in
`ScreenshotOrchestrator` after each tracker completes. The `:progress`/`:total` Clique tokens reflect raw tick counts, not tracker counts — users
should rely on the appended `X/Y` suffix for tracker-level progress.

The screenshot phase is always one tick regardless of how many redaction types are configured — this is intentional, because
whether a handler has any non-empty redaction interface (`RedactionRegistry.hasAnyRedaction(handler)`) is determined at runtime,
making the actual screenshot count unknowable upfront.

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

Linter configs live in `ci/`:

- `ci/java/checkstyle.xml` and `checkstyle-suppression.xml`
- `ci/java/pmd-ruleset.xml`
- `ci/java/spotbugs-include/exclude-filter-file.xml`
- `ci/docker/.hadolint.yaml`

Suppress only when genuinely necessary. The codebase has very few suppressions — keep it that way.

Markdown files follow `ci/doc/.markdownlint.json`: body lines max 150 characters, heading lines max 120 characters, code block lines max 200
characters (tables are exempt). Always run the IDE formatter (Ctrl+Shift+F) on any `.md` file after editing.

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

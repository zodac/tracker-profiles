---
name: Orchestrator
description: Coordinates the login, profile, and redactor agents to produce a complete TrackerHandler implementation
model: claude-haiku-4-5-20251001
type: project
---

# Orchestrator Agent

You are the orchestration agent for a new `TrackerHandler` implementation. Your job is to coordinate
specialist agents, assemble their findings into a complete Java handler class, and complete the
post-implementation checklist.

## Workflow

### Starting

Use the following prompt:

```text
Implement a new TrackerHandler for `TrackerName` (https://tracker.site/, credentials: username/password).

Start by reading .claude/agents/orchestrator.md then coordinate the specialist agents.

Notes: Add any additional notes specific to this tracker here.
```

### Step 1 — Determine base class

Before spawning agents, inspect existing handlers to determine whether this tracker uses a known platform:

- **UNIT3D** → extend `Unit3dHandler`
- **Gazelle** → extend `GazelleHandler`
- **NexusPHP** → extend `NexusPhpHandler`
- **Luminance** → extend `LuminanceHandler`
- **TorrentPier** → extend `TorrentPier`
- **Unknown** → extend `AbstractTrackerHandler`

Read a similar existing handler (same base class) as a structural reference before writing anything.

### Cloudflare sites

If any specialist agent returns `BLOCKED: Cloudflare verification required`, **stop immediately** and
report to the user:

> This tracker is protected by an interactive Cloudflare challenge and cannot be implemented
> automatically. Please inspect the site manually in a browser and provide the selectors, or
> skip this tracker.

Do not attempt to infer selectors, spawn additional agents, or write a partial handler.

### Step 2 — Spawn specialist agents sequentially

Spawn each agent with its context file path and the tracker name/URL. Collect findings before proceeding.

1. **Login agent** → `.claude/agents/login_agent.md`
2. **Profile agent** → `.claude/agents/profile_agent.md`
3. **Page structure agent** → `.claude/agents/page_structure_agent.md` *(conditional — see below)*
4. **Redactor agent** → `.claude/agents/redactor_agent.md`

#### When to invoke the page structure agent

The profile agent's output includes three flags: `Fixed header`, `Fixed sidebar`, `Cookie banner`. Invoke
the page structure agent if **any flag is `YES` or `UNSURE`**. Skip it only if all three are `NO`.

The page structure agent needs the same credentials and profile page URL as the other agents. Pass these
along with the profile agent's flags so it knows which elements have already been identified.

### Step 3 — Write the handler class

First, decide whether a new class is needed at all:

- **No method overrides required** (tracker uses the base class defaults in every respect) → add a
  `@TrackerHandler(name = "Name", url = "https://...")` annotation directly to the base handler class
  (`Unit3dHandler`, `GazelleHandler`, etc.) in alphabetical order among its existing annotations.
  **Do not create a new `.java` file.**
- **At least one method must be overridden** → create a new `.java` file as described below.

Write the final `.java` file to:
`tracker-profiles-screenshots/src/main/java/net/zodac/tracker/handler/<TrackerName>.java`

Rules:

- BSD Zero Clause License header (copy from any existing handler)
- Package: `net.zodac.tracker.handler`
- Annotation: `@TrackerHandler(name = "Name", url = "https://...")`
    - For multiple fallback URLs: `url = {"https://url1", "https://url2"}`
    - For MANUAL type: add `type = TrackerType.MANUAL`
- Declare `implements` for every interface the specialist agents flagged as needed:
    - Redaction (`net.zodac.tracker.handler.redaction.*`): `RedactsEmail`, `RedactsIpAddress`,
      `RedactsTorrentPasskey`, `RedactsIrcPasskey`, `RedactsSensitiveInformation` — one per category present
    - Structural (`net.zodac.tracker.handler.definition.*`): `HasFixedHeader`, `HasFixedSidebar`,
      `HasDismissibleElement`, `HasJumpButtons`, `HasProfilePageActions`, `HasCloudflareCheck`,
      `NeedsExplicitTranslation`, `UsesExtensions`, `DoesNotScrollDuringScreenshot` — as applicable
- Only include overrides that differ from the base class defaults; the interface methods are `public`,
  the `AbstractTrackerHandler` selectors (`loginButtonSelector`, `profileLinkSelector`, etc.) are `protected`
- Use `XpathBuilder` exclusively — never raw XPath strings
- Imports must be sorted (static first, then standard, alphabetical within each group)
- Do not add comments unless the logic is genuinely non-obvious

### Step 4 — Complete the checklist

After writing the handler:

1. Add a CSV entry to `./trackers_example.csv` (alphabetical order)
2. Add a row to the correct table in `README.md` (alphabetical order):
    - `HEADLESS` type → **"### Headless"** multi-column table (find the right letter-range column)
    - `MANUAL` type → **"### Manual Interaction"** table
    - Do **not** insert by finding the nearest alphabetical neighbour in the file — that neighbour may be
      in the wrong section. Always locate the correct section first, then find the alphabetical position
      within it.
3. Increment the tracker count on the "There are currently **N** supported trackers" line in `README.md`
4. Run tests and lints

## Key defaults (only override if different)

| Method                    | Default                                                                                       |
|---------------------------|-----------------------------------------------------------------------------------------------|
| `usernameFieldSelector()` | `By.name("username")`                                                                         |
| `passwordFieldSelector()` | `By.name("password")`                                                                         |
| `loginButtonSelector()`   | `By.id("login-button")`                                                                       |
| `postLoginSelector()`     | delegates to `profileLinkSelector()` — **must override** if profile selector has side effects |
| `profilePageSelector()`   | `<a class="username">` — almost always needs overriding                                       |

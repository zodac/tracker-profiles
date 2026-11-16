# Manual Input Card Not Shown In The Web UI

**Status:** Open, cause not found yet (investigated 2026-11-16, at commit `854bf0b`)

## Symptom

Reproduced on **BeyondHD** (`MANUAL`, captcha in `preLoginClickAction()`):

- The captcha element is highlighted (red border) in the tracker's browser window
- No input card ("BeyondHD needs you", with Continue/Skip/+5 min) appears in the web UI
- The user did not enter anything, and the page in the browser did not change (so `DisplayUtils.hasPageChanged` should not have ended the
  request early)

## Expected Flow

1. `BeyondHd.preLoginClickAction()` → `awaitUserInput(captchaSelector, "Solve the captcha")`
2. `AbstractTrackerHandler.awaitUserInput(By, String)` → `driver().findElement(...)` → `awaitUserInput(WebElement, String)`
3. `browserInteractionHelper().highlightElement(...)` (the highlight is seen, so steps 1-3 run)
4. `DisplayUtils.confirm(trackerName, instruction)` reads the initial URL/title (`driver.getCurrentUrl()`, `driver.getTitle()`)
5. `UserInputRequests.await(...)` → `register(...)` adds the request to `PENDING`, then logs
   `Waiting for user input in the web UI: Solve the captcha` (INFO, so also the tracker's latest message in `RunStatus`)
6. `waitForResponse(...)` polls every 500ms (response, timeout, cancellation, page change)
7. `RunManager.status()` includes `UserInputRequests.pending()` as `RunStatus.pendingInputs`, served by `GET /api/run`
8. `run.mjs` `renderStatus` → `renderPendingInputs(running ? status.pendingInputs : [])` → `createInputCard` in `#pending-inputs` (top
   of the Main tab), polled every 2s while running (1s while an input is pending, 10s while the page is hidden)

## Verified Working (In Isolation)

| Link                                  | How it was checked                                                                                                                                    |
|---------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `awaitUserInput` refactor (`2b9a128`) | Diff: same behaviour as the previous per-handler highlight + `displayUtils().confirm(...)` code                                                       |
| `UserInputRequests`                   | `UserInputRequestsTest` and `RunManagerTest` pass                                                                                                     |
| `RunManager.status()` → JSON          | Temporary test: with a pending `await("BeyondHD", ...)`, `ApiHandler.toJson(status)` contains the input                                               |
| Lock ordering                         | `WebLogAppender.lock` is a leaf (never calls out while held), so no deadlock with `RunManager`/`UserInputRequests`                                    |
| Frontend rendering                    | Real `web/` files served with a stub `/api/run` (state `RUNNING`, one pending input), loaded in headless Chromium with `--dump-dom`: the card renders |
| Frontend wiring                       | Every `byId(...)` ID exists in `index.html`, every module import has a matching export                                                                |
| Card markup/CSS                       | `#pending-inputs` position and `.pending-inputs` CSS unchanged since `b7e36e1`; logic unchanged by the `.mjs` split (`573ad9d`)                       |
| Latest commit (`854bf0b`)             | Only field reordering in `RunManager` and log changes                                                                                                 |

## Next Steps

Rerun BeyondHD (ideally with `LOG_LEVEL=TRACE`) and, while the captcha is highlighted, record:

1. **Header pill**: `Waiting for you` (status arrives, only the card is missing) or `Running`
2. **BeyondHD row**: whether it has the `Needs you` badge, and its latest message
3. **BeyondHD logs**: whether `Waiting for user input in the web UI: Solve the captcha` is logged, and any TRACE lines from `DisplayUtils`
   / `UserInputRequests` (`Starting page change listener`, `Woke waiting for request #N`, `Page change detected`)
4. **`/api/run`** opened in the same browser: `state` and `pendingInputs`
5. **Which image is running**: the local build (`docker-compose.yml`) or the released image (`docs/docker-compose.example.yml`), which may
   not contain the current code

How to read the results:

- **Not logged, no badge** → the request is never registered: the thread is stuck or failing between the highlight and `register` (e.g.
  `driver.getCurrentUrl()`/`getTitle()` in `DisplayUtils.confirm` blocking, or an exception; check for a failure/stack trace)
- **Logged, but `/api/run` has no `pendingInputs`** → the request is removed early (check the `finally` in `UserInputRequests.await`, or
  a page change/timeout)
- **`/api/run` has the input, but no card** → browser-side issue (cached old `.mjs`, a JS error in `renderStatus` before
  `renderPendingInputs`: check the browser console)

## Reproducing The Frontend Check

Serve `tracker-profiles-screenshots/src/main/resources/web` with a small HTTP server whose `GET /api/run` returns a `RunStatus` with
`"state":"RUNNING"` and a `pendingInputs` entry (`id`, `trackerName`, `message`, `requestedAt`, `expiresAt`), other `/api/*` returning
`[]`/`{}`, and `.mjs` served as `text/javascript`. Then:

```bash
chromium --headless --no-sandbox --disable-gpu --virtual-time-budget=5000 --dump-dom http://127.0.0.1:8099/ \
    | grep -o '<div id="pending-inputs".\{0,300\}'
```

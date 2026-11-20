export const MAXIMUM_LOG_LINES = 5000
export const MAXIMUM_TOASTS = 4
export const STATUS_POLL_INTERVAL_MS = 2000
export const IDLE_POLL_INTERVAL_MS = 10000
export const INPUT_POLL_INTERVAL_MS = 1000
// While the page is hidden, the status is only checked during an execution (so the page title still shows when an input is needed)
export const HIDDEN_POLL_INTERVAL_MS = 10000
// How long a live browser view waits before trying again, when the browser is not open or cannot be reached
export const BROWSER_VIEW_RETRY_MS = 2000
// The longest text that can be pasted into a live browser view at once (see BrowserInputEvent)
export const MAXIMUM_PASTED_TEXT_LENGTH = 1000
// The zoom levels of a live browser view (1 shows each pixel of the browser as one pixel of the page), and where the chosen zoom is remembered,
// separately for the interactive view of an input and the view-only panel
export const BROWSER_VIEW_ZOOM_LEVELS = [0.5, 0.75, 1, 1.25, 1.5, 2, 2.5, 3]
export const BROWSER_VIEW_ZOOM_STORAGE_KEY = 'tracker-profiles-browser-zoom'
export const TYPING_SAVE_DELAY_MS = 1000
export const THEME_STORAGE_KEY = 'tracker-profiles-theme'
// Shown (masked) in the password field of a tracker with a saved password, which is never sent to the browser. Private-use characters, so it
// cannot match a password that is typed
export const SAVED_PASSWORD_VALUE = '\uE000'.repeat(8)
// How long a tracker whose credentials have just been set stays in its group, while not being changed, before moving
export const UNSET_MOVE_DELAY_MS = 10000
export const PAGE_TITLE = document.title
export const DARK_SCHEME_QUERY = window.matchMedia('(prefers-color-scheme: dark)')
export const REDUCED_MOTION_QUERY = window.matchMedia('(prefers-reduced-motion: reduce)')
export const STATE_LABELS = {
    IDLE: 'Idle',
    RUNNING: 'Running',
    WAITING: 'Waiting for you',
    CANCELLING: 'Cancelling',
    CANCELLED: 'Cancelled',
    SUCCESS: 'Succeeded',
    PARTIAL_FAILURE: 'Partially failed',
    FAILURE: 'Failed',
}
// The steps of each tracker, in order (see TrackerStep)
export const TRACKER_STEPS = [
    {name: 'OPEN_TRACKER', label: 'Opening tracker'},
    {name: 'LOGIN', label: 'Logging in'},
    {name: 'OPEN_PROFILE_PAGE', label: 'Opening profile'},
    {name: 'TAKE_SCREENSHOTS', label: 'Taking screenshots'},
    {name: 'LOGOUT', label: 'Logging out'},
]
// The groups of trackers in an execution, in the order they are shown
export const TRACKER_GROUPS = [
    {state: 'RUNNING', title: 'In progress', empty: null},
    {state: 'WAITING', title: 'Waiting', titleWhenFinished: 'Not run'},
    {state: 'FAILED', title: 'Failed'},
    {state: 'CANCELLED', title: 'Cancelled'},
    {state: 'SUCCEEDED', title: 'Succeeded'},
]
export const LOG_LEVEL_SETTING = 'LOG_LEVEL'

import {
    HIDDEN_POLL_INTERVAL_MS,
    IDLE_POLL_INTERVAL_MS,
    INPUT_POLL_INTERVAL_MS,
    PAGE_TITLE,
    STATE_LABELS,
    STATUS_POLL_INTERVAL_MS,
} from './constants.mjs'
import {createBrowserView, startBrowserView} from './browser-view.mjs'
import {refreshOpenLogs, renderExecutionTrackers} from './execution-trackers.mjs'
import {updateExecutionDownload} from './screenshots.mjs'
import {applyRunStateToSettings} from './settings.mjs'
import {loadTrackers, trackers, trackersLoaded} from './trackers.mjs'
import {byId, el, pluralise, request, showToast} from './util.mjs'

// ---------------------------------------------------------------------------------------------------------------
// Main: execution status
// ---------------------------------------------------------------------------------------------------------------

export let statusTimer = null
export let latestStatus = null
// Whether the last status request failed, so the error is only shown once while the application cannot be reached
let statusUnreachable = false

export function isActive(status) {
    return Boolean(status) && (status.state === 'RUNNING' || status.state === 'CANCELLING')
}

export function formatDuration(milliseconds) {
    const totalSeconds = Math.max(0, Math.round(milliseconds / 1000))
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return minutes > 0 ? `${minutes}m ${String(seconds).padStart(2, '0')}s` : `${seconds}s`
}

export function formatTypeName(value) {
    const words = value.toLowerCase().replace(/_/g, ' ')
    return words.charAt(0).toUpperCase() + words.slice(1)
}

function enabledTrackersDescription() {
    if (!trackersLoaded) {
        return 'Takes a screenshot of the profile page of every enabled tracker.'
    }

    // Legacy trackers are never executed, even if they were enabled before they stopped being supported
    const enabled = trackers.filter(function (tracker) {
        return tracker.enabled && !tracker.legacy
    })
    if (enabled.length === 0) {
        return 'No trackers are enabled yet. Enable them, or import a CSV file, on the Trackers tab.'
    }

    const manual = enabled.filter(function (tracker) {
        return tracker.type === 'MANUAL'
    }).length
    return `${pluralise(enabled.length, 'enabled tracker')}: ${enabled.length - manual} headless, ${manual} manual.`
}

export function runDescription(status) {
    if (status.state === 'CANCELLING') {
        return 'Cancelling: closing all browsers and stopping in-progress trackers…'
    }
    if (status.state === 'RUNNING') {
        const currentType = status.startedTrackerTypes[status.startedTrackerTypes.length - 1]
        const elapsed = formatDuration(Date.now() - status.startedAt)
        const typeDescription = currentType ? ` · ${formatTypeName(currentType)} trackers` : ''
        return `Started at ${new Date(status.startedAt).toLocaleTimeString()} · ${elapsed} elapsed${typeDescription}`
    }

    const description = enabledTrackersDescription()
    if (status.state === 'CANCELLED' && status.finishedAt) {
        return `${description} Last execution cancelled at ${new Date(status.finishedAt).toLocaleTimeString()}.`
    }
    if (status.finishedAt) {
        return `${description} Last execution ${(STATE_LABELS[status.state] || '').toLowerCase()} at `
            + `${new Date(status.finishedAt).toLocaleTimeString()} (took ${formatDuration(status.finishedAt - status.startedAt)}).`
    }
    return description
}

function renderStatus(status) {
    const wasActive = isActive(latestStatus)
    latestStatus = status
    const running = status.state === 'RUNNING'
    const cancelling = status.state === 'CANCELLING'
    const active = running || cancelling
    const waiting = running && status.pendingInputs.length > 0

    const headerState = waiting ? 'WAITING' : status.state
    const headerStatus = byId('header-status')
    headerStatus.dataset.state = headerState
    headerStatus.textContent = STATE_LABELS[headerState] || headerState
    document.title = waiting ? `(Input needed) ${PAGE_TITLE}` : PAGE_TITLE

    const startButton = byId('start-button')
    startButton.disabled = active
    startButton.textContent = active ? 'Running…' : 'Start'

    // Only the trackers that failed in the previous execution can be executed again, once it has ended
    const failedTrackers = status.failedTrackers
    const startFailedButton = byId('start-failed-button')
    startFailedButton.hidden = active || failedTrackers.length === 0
    startFailedButton.disabled = active
    startFailedButton.textContent = `Failures only (${failedTrackers.length})`
    const failedDescription = pluralise(failedTrackers.length, 'tracker')
    startFailedButton.title = `Execute only the ${failedDescription} that failed in the previous execution: ${failedTrackers.join(', ')}`

    const cancelButton = byId('cancel-button')
    cancelButton.hidden = !active
    cancelButton.disabled = cancelling
    cancelButton.textContent = cancelling ? 'Cancelling…' : 'Cancel'

    byId('run-description').textContent = runDescription(status)

    const succeeded = countTrackers(status, 'SUCCEEDED')
    const failed = countTrackers(status, 'FAILED')
    const completed = succeeded + failed
    byId('run-progress').hidden = status.state === 'IDLE'
    byId('progress-fill').style.width = status.totalTrackers > 0 ? `${Math.round((completed / status.totalTrackers) * 100)}%` : '0'
    if (status.state === 'CANCELLED') {
        byId('progress-text').textContent = status.totalTrackers > 0
            ? `Cancelled after ${completed} of ${pluralise(status.totalTrackers, 'tracker')}`
            : 'Cancelled before any tracker was executed'
    } else {
        byId('progress-text').textContent = status.totalTrackers > 0
            ? `${completed} of ${pluralise(status.totalTrackers, 'tracker')} complete`
            : (active ? 'Preparing…' : 'No trackers executed')
    }
    byId('success-count').textContent = String(succeeded)
    byId('failure-count').textContent = String(failed)

    renderPendingInputs(running ? status.pendingInputs : [])
    renderExecutionTrackers(status)
    updateExecutionDownload(status)
    applyRunStateToSettings(status)
    byId('execution-log').hidden = status.state === 'IDLE'
    refreshOpenLogs(active || wasActive)

    // The results of the trackers are shown on the Trackers tab, so are reloaded once an execution ends
    if (wasActive && !active && trackersLoaded) {
        loadTrackers().catch(function (error) {
            showToast(error.message, 'error')
        })
    }

    // While idle, the status is still checked occasionally, in case an execution is started from another browser
    if (active) {
        scheduleStatusRefresh(cancelling ? 500 : (waiting ? INPUT_POLL_INTERVAL_MS : STATUS_POLL_INTERVAL_MS))
    } else {
        scheduleStatusRefresh(IDLE_POLL_INTERVAL_MS)
    }
}

function countTrackers(status, state) {
    return status.trackers.filter(function (tracker) {
        return tracker.state === state
    }).length
}

export function refreshStatus() {
    return request('GET', '/api/run')
        .then(function (status) {
            if (statusUnreachable) {
                statusUnreachable = false
                showToast('Reconnected to the application')
            }
            renderStatus(status)
        })
        .catch(function (error) {
            // Shown once, rather than on every retry, until the application is reachable again
            if (!statusUnreachable) {
                statusUnreachable = true
                showToast(`Unable to reach the application, retrying: ${error.message}`, 'error')
            }
            scheduleStatusRefresh(IDLE_POLL_INTERVAL_MS)
        })
}

// While the page is hidden, the status is only checked slowly during an execution (and logs are not fetched), and it is refreshed as soon as
// the page is shown again (see the 'visibilitychange' listener)
export function scheduleStatusRefresh(delay) {
    clearTimeout(statusTimer)
    statusTimer = null
    if (!document.hidden) {
        statusTimer = setTimeout(refreshStatus, delay)
    } else if (isActive(latestStatus)) {
        statusTimer = setTimeout(refreshStatus, HIDDEN_POLL_INTERVAL_MS)
    }
}

export function startRun(failedTrackersOnly) {
    byId('start-button').disabled = true
    byId('start-failed-button').disabled = true
    request('POST', failedTrackersOnly ? '/api/run/failed' : '/api/run', {})
        .then(renderStatus)
        .catch(function (error) {
            showToast(error.message, 'error')
            refreshStatus()
        })
}

export function cancelRun() {
    if (!window.confirm('Cancel the current execution? All browsers will be closed, and trackers in progress will not be screenshot.')) {
        return
    }

    byId('cancel-button').disabled = true
    request('POST', '/api/run/cancel', {})
        .then(function (status) {
            showToast('Cancelling execution')
            renderStatus(status)
        })
        .catch(function (error) {
            showToast(error.message, 'error')
            refreshStatus()
        })
}

// ---------------------------------------------------------------------------------------------------------------
// Main: requests for a user input (manual trackers)
// ---------------------------------------------------------------------------------------------------------------

function respondToInput(input, action, button) {
    button.disabled = true
    request('POST', `/api/run/input/${input.id}/${action}`, {})
        .then(function (status) {
            if (action === 'skip') {
                showToast(`Skipped ${input.trackerName}`)
            } else {
                showToast(`Continuing ${input.trackerName}`)
            }
            renderStatus(status)
        })
        .catch(function (error) {
            showToast(error.message, 'error')
            refreshStatus()
        })
        .finally(function () {
            button.disabled = false
        })
}

function createInputCard(input) {
    const skipButton = el('button', {
        className: 'button', type: 'button', text: 'Skip tracker', title: 'Fail this attempt for the tracker, and move on',
    })
    const continueButton = el('button', {className: 'button primary', type: 'button', text: 'Continue'})
    skipButton.addEventListener('click', function () {
        respondToInput(input, 'skip', skipButton)
    })
    continueButton.addEventListener('click', function () {
        respondToInput(input, 'continue', continueButton)
    })

    // The tracker's browser is shown in the card, and the user's input in it is sent to the browser
    const browserView = createBrowserView(input.trackerName, true)
    const card = el('div', {className: 'input-card', role: 'alert', dataset: {id: String(input.id)}}, [
        el('div', {className: 'input-header'}, [
            el('div', {className: 'input-text'}, [
                el('h2', {text: `${input.trackerName} needs you`}),
                el('p', {text: `${input.message} in the browser below, then select Continue.`}),
                el('p', {
                    className: 'muted',
                    text: 'Click in the browser to use it: typing is sent to it while it is selected, and Ctrl+V pastes your clipboard. Zoom in '
                        + 'with its toolbar or Ctrl+scroll, or show it on the whole screen. Skipping fails this attempt for the tracker. Continuing is '
                        + 'automatic if the page changes.',
                }),
            ]),
            el('div', {className: 'input-actions'}, [skipButton, continueButton]),
        ]),
        browserView,
    ])
    card.browserView = browserView
    return card
}

// Cards are updated in place (not re-rendered), so a focused button is never replaced
function renderPendingInputs(pendingInputs) {
    const container = byId('pending-inputs')
    const pendingIds = pendingInputs.map(function (input) {
        return String(input.id)
    })
    Array.from(container.children).forEach(function (card) {
        if (!pendingIds.includes(card.dataset.id)) {
            card.remove()
        }
    })

    pendingInputs.forEach(function (input) {
        let card = container.querySelector(`[data-id="${input.id}"]`)
        if (!card) {
            card = createInputCard(input)
            container.append(card)
            startBrowserView(card.browserView)
        }
    })
}

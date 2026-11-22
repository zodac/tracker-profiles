import {createBrowserView, startBrowserView, stopBrowserView} from './browser-view.mjs'
import {REDUCED_MOTION_QUERY, TRACKER_GROUPS, TRACKER_STEPS} from './constants.mjs'
import {
    activeDuration,
    enabledTrackers,
    ESTIMATE_DESCRIPTION,
    formatDuration,
    formatEstimate,
    isActive,
    latestStatus,
    refreshStatus,
    updateRunCardCondensed,
} from './run.mjs'
import {updateTrackerScreenshots} from './screenshots.mjs'
import {currentSettingValue} from './settings.mjs'
import {trackersLoaded} from './trackers.mjs'
import {applyConfirmation, byId, confirmThenRun, el, formatTime, pluralise, request, showToast} from './util.mjs'

// ---------------------------------------------------------------------------------------------------------------
// Main: trackers in the execution, grouped by state, each with its own logs
// ---------------------------------------------------------------------------------------------------------------

// Rows are kept between renders (and moved between groups), so an expanded tracker stays expanded, keyed by tracker name. They are also kept when
// an execution starts, so the trackers waiting before it are the same rows once it has started (a row executed again is reset)
const trackerRows = new Map()
const groupElements = {}

function stepBar(tracker) {
    const nextStep = TRACKER_STEPS.find(function (step) {
        return !tracker.completedSteps.includes(step.name)
    })
    return el('span', {className: 'steps', 'aria-label': `${tracker.completedSteps.length} of ${TRACKER_STEPS.length} steps complete`},
        TRACKER_STEPS.map(function (step) {
            let state = ''
            if (tracker.completedSteps.includes(step.name)) {
                state = 'done'
            } else if (step === nextStep && tracker.state !== 'WAITING') {
                state = tracker.state === 'RUNNING' ? 'current' : 'stopped'
            }
            return el('span', {className: `step ${state}`, title: step.label})
        }))
}

// An execution has ended (rather than not started yet, or in progress)
function isFinished(status) {
    return status.state !== 'IDLE' && !isActive(status)
}

// Before the first execution, the enabled trackers are shown waiting, as they would be once it starts
// The enabled trackers, in the order they will be executed (see ExecutionOrder): by name, or one type first and then each type by name
export function waitingTrackers(executionOrder) {
    const firstType = {HEADLESS_FIRST: 'HEADLESS', MANUAL_FIRST: 'MANUAL'}[executionOrder]
    const sorted = enabledTrackers().slice().sort(function (first, second) {
        if (firstType && first.type !== second.type) {
            return first.type === firstType ? -1 : 1
        }
        // As TrackerCredential.compareTo, so the order matches the execution
        const firstName = first.name.toLowerCase()
        const secondName = second.name.toLowerCase()
        return firstName < secondName ? -1 : (firstName > secondName ? 1 : 0)
    })
    return sorted.map(function (tracker) {
        return {
            name: tracker.name,
            type: tracker.type,
            state: 'WAITING',
            attempt: 0,
            completedSteps: [],
            stepCompletedAt: {},
            startedAt: null,
            finishedAt: null,
            estimatedDuration: tracker.estimatedDuration,
            inputWait: 0,
            latestMessage: '',
            screenshots: [],
        }
    })
}

function hasEstimate(tracker) {
    return tracker.estimatedDuration !== null && tracker.estimatedDuration !== undefined
}

// A tracker waiting (before a full execution, or once one has started) shows how long it is estimated to take, and a running one the time it has
// been running (not counting the time waiting for an input) against its estimate
export function trackerTiming(tracker) {
    if (tracker.state === 'WAITING') {
        return hasEstimate(tracker) ? formatEstimate(tracker.estimatedDuration) : ''
    }
    if (tracker.startedAt === null) {
        return ''
    }
    if (tracker.state === 'RUNNING' && hasEstimate(tracker)) {
        return `${formatDuration(activeDuration(tracker))} / ${formatEstimate(tracker.estimatedDuration)}`
    }
    const end = tracker.finishedAt === null ? Date.now() : tracker.finishedAt
    return formatDuration(end - tracker.startedAt)
}

function trackerTimingTitle(tracker) {
    if ((tracker.state !== 'WAITING' && tracker.state !== 'RUNNING') || !hasEstimate(tracker)) {
        return null
    }
    return `Estimated to take ${formatDuration(tracker.estimatedDuration)}: ${ESTIMATE_DESCRIPTION}`
}

function trackerDetail(tracker) {
    if (tracker.state === 'WAITING') {
        return latestStatus && isFinished(latestStatus) ? 'Not run' : 'Waiting'
    }
    if (tracker.state === 'SUCCEEDED') {
        return tracker.screenshots.length === 0 ? '' : pluralise(tracker.screenshots.length, 'screenshot')
    }
    const attempts = currentSettingValue('NUMBER_OF_SCREENSHOT_ATTEMPTS')
    const total = attempts ? `/${attempts}` : ''
    const attempt = tracker.attempt > 1 ? `Attempt ${tracker.attempt}${total} · ` : ''
    if (tracker.state === 'RUNNING') {
        const nextStep = TRACKER_STEPS.find(function (step) {
            return !tracker.completedSteps.includes(step.name)
        })
        return `${attempt}${tracker.latestMessage || (nextStep ? nextStep.label : 'Finishing')}`
    }
    return `${attempt}${tracker.latestMessage}`
}

// The live view of a running tracker's browser, only streamed while it is open (it is closed by default, as it is large)
function createBrowserPanel(tracker) {
    const panel = el('details', {className: 'tracker-browser', hidden: true}, [
        el('summary', {}, [
            el('span', {className: 'tracker-browser-title', text: 'Live browser'}),
            el('span', {className: 'muted', text: 'View only: the browser can be used when the tracker needs you'}),
        ]),
    ])
    panel.addEventListener('toggle', function () {
        updateBrowserPanel(panel, tracker.name)
    })
    return panel
}

function updateBrowserPanel(panel, trackerName) {
    // Not streamed while the tracker's row is collapsed either, as it is then not visible
    const row = panel.closest('.execution-tracker')
    const shown = !panel.hidden && panel.open && Boolean(row) && row.open
    if (shown && !panel.browserView) {
        panel.browserView = createBrowserView(trackerName, false)
        panel.append(panel.browserView)
        startBrowserView(panel.browserView)
    } else if (!shown && panel.browserView) {
        stopBrowserView(panel.browserView)
        panel.browserView.remove()
        panel.browserView = null
    }
}

function createExecutionRow(tracker) {
    const summary = el('summary', {className: 'execution-row'})
    const browser = createBrowserPanel(tracker)
    const screenshots = el('div', {className: 'tracker-screenshots', hidden: true})
    const row = el('details', {className: 'execution-tracker', dataset: {name: tracker.name}}, [summary, browser, screenshots, createPhaseLog(tracker)])
    // A waiting tracker has no logs yet, so cannot be expanded
    summary.addEventListener('click', function (event) {
        if (row.dataset.state === 'WAITING') {
            event.preventDefault()
        }
    })
    row.addEventListener('toggle', function () {
        if (row.open) {
            fetchLogs(row.querySelector('.tracker-log'))
        }
        updateBrowserPanel(browser, tracker.name)
    })
    return row
}

// A tracker executed again (waiting once more, after it had started) starts with no logs, collapsed
function resetRowIfExecutedAgain(row, tracker) {
    if (tracker.startedAt === null && row.trackerStartedAt) {
        row.open = false
        row.querySelector('.tracker-log').replaceWith(createPhaseLog(tracker))
        delete logCursors[tracker.name]
        row.cancelling = false
    }
    row.trackerStartedAt = tracker.startedAt
}

// Stops a single running tracker (its browser is closed and it fails), while the rest of the execution continues
function cancelTracker(row, trackerName) {
    row.cancelling = true
    request('POST', `/api/run/cancel/${encodeURIComponent(trackerName)}`, {})
        .then(function () {
            showToast(`Cancelling ${trackerName}`)
            refreshStatus()
        })
        .catch(function (error) {
            row.cancelling = false
            showToast(error.message, 'error')
            refreshStatus()
        })
}

function cancelTrackerButton(row, tracker) {
    if (tracker.state !== 'RUNNING') {
        return null
    }
    if (row.cancelling) {
        return el('button', {className: 'button small danger-solid', type: 'button', text: 'Cancelling…', disabled: true})
    }
    // Asks for confirmation first (see confirmThenRun), and stays armed when the row is re-rendered
    const key = `tracker:${tracker.name}`
    const button = el('button', {
        className: 'button small danger-solid', type: 'button', dataset: {confirmKey: key, confirmLabel: 'Cancel'},
        title: `Stop ${tracker.name} now, closing its browser (it fails, so Failures only executes it again)`,
    })
    applyConfirmation(button)
    button.addEventListener('click', function (event) {
        // In the row's summary, so the row is not expanded or collapsed
        event.preventDefault()
        event.stopPropagation()
        confirmThenRun(key, function () {
            button.disabled = true
            button.textContent = 'Cancelling…'
            cancelTracker(row, tracker.name)
        })
    })
    return button
}

function updateExecutionRow(row, tracker) {
    resetRowIfExecutedAgain(row, tracker)
    const summary = row.querySelector('summary')
    const needsInput = latestStatus && latestStatus.pendingInputs.some(function (input) {
        return input.trackerName === tracker.name
    })
    summary.replaceChildren(
        el('span', {className: 'execution-name'}, [
            tracker.name,
            tracker.type === 'MANUAL' ? el('span', {className: 'badge manual', text: 'Manual input'}) : null,
            needsInput ? el('span', {className: 'badge needs-input', text: 'Needs you'}) : null,
        ]),
        stepBar(tracker),
        el('span', {className: 'execution-detail muted', text: trackerDetail(tracker), title: tracker.latestMessage}),
        el('span', {className: 'execution-end'}, [
            cancelTrackerButton(row, tracker),
            el('span', {className: 'execution-time muted', text: trackerTiming(tracker), title: trackerTimingTitle(tracker)}),
        ])
    )
    row.dataset.state = tracker.state
    const browser = row.querySelector('.tracker-browser')
    browser.hidden = tracker.state !== 'RUNNING'
    if (browser.hidden) {
        browser.open = false
    }
    updateBrowserPanel(browser, tracker.name)
    updateTrackerScreenshots(row.querySelector('.tracker-screenshots'), tracker)
    updatePhases(row.querySelector('.tracker-log'), tracker)
}

// ---------------------------------------------------------------------------------------------------------------
// Main: tracker logs, grouped by step (phase)
// ---------------------------------------------------------------------------------------------------------------

function createPhaseLog(tracker) {
    const phases = TRACKER_STEPS.map(function (step) {
        const summary = el('summary', {}, [
            el('span', {className: 'phase-icon', 'aria-hidden': 'true'}),
            el('span', {className: 'phase-name', text: step.label}),
            el('span', {className: 'phase-count'}),
            el('span', {className: 'phase-time'}),
        ])
        const phase = el('details', {className: 'phase', dataset: {step: step.name, count: '0', warnings: '0'}},
            [summary, el('div', {className: 'phase-body'})])
        // A phase with no messages has nothing to show
        summary.addEventListener('click', function (event) {
            if (phase.dataset.count === '0') {
                event.preventDefault()
            }
        })
        return phase
    })
    return el('div', {className: 'log-output tracker-log', role: 'log', dataset: {tracker: tracker.name}},
        [el('div', {className: 'log-empty', text: 'Loading logs…'})].concat(phases, [el('div', {className: 'phase-footer'})]))
}

function phaseState(tracker, step, nextStep) {
    if (tracker.completedSteps.includes(step.name)) {
        return 'done'
    }
    if (step === nextStep) {
        return {RUNNING: 'running', FAILED: 'failed', CANCELLED: 'cancelled'}[tracker.state] || 'pending'
    }
    return tracker.state === 'RUNNING' || tracker.state === 'WAITING' ? 'pending' : 'skipped'
}

// The time from the end of the previous step (or the start of the tracker) to the end of this step (or now, if in progress)
function phaseDuration(tracker, index, state) {
    if (state === 'pending' || state === 'skipped' || tracker.startedAt === null) {
        return ''
    }
    const previous = index === 0 ? null : tracker.stepCompletedAt[TRACKER_STEPS[index - 1].name]
    const start = previous || tracker.startedAt
    let end = tracker.stepCompletedAt[TRACKER_STEPS[index].name]
    if (!end) {
        end = tracker.finishedAt === null ? Date.now() : tracker.finishedAt
    }
    return formatDuration(end - start)
}

function updatePhases(output, tracker) {
    const nextStep = TRACKER_STEPS.find(function (step) {
        return !tracker.completedSteps.includes(step.name)
    })
    if (tracker.attempt > 1) {
        output.classList.add('multi-attempt')
    }
    TRACKER_STEPS.forEach(function (step, index) {
        const phase = output.querySelector(`.phase[data-step="${step.name}"]`)
        const state = phaseState(tracker, step, nextStep)
        // Opened when it starts running or fails, and closed when done, so a phase opened or closed by the user stays that way until it changes
        if (phase.dataset.state !== state) {
            phase.dataset.state = state
            phase.open = phase.dataset.count !== '0' && (state === 'running' || state === 'failed' || state === 'cancelled')
        }
        phase.querySelector('.phase-icon').textContent = {done: '✓', failed: '✕', skipped: '–'}[state] || ''
        phase.querySelector('.phase-time').textContent = phaseDuration(tracker, index, state)
        updatePhaseCount(phase)
    })
}

function updatePhaseCount(phase) {
    const count = Number(phase.dataset.count)
    const warnings = Number(phase.dataset.warnings)
    let text = count === 0 ? '' : `${count} message${count === 1 ? '' : 's'}`
    if (count === 0 && phase.dataset.state === 'skipped') {
        text = 'Not run'
    }
    phase.querySelector('.phase-count').replaceChildren(text, warnings === 0 ? '' : el('span', {
        className: 'phase-warnings', text: ` · ${warnings} warning${warnings === 1 ? '' : 's'}`,
    }))
    phase.classList.toggle('empty', count === 0)
}

function appendPhaseEntries(output, entries) {
    entries.forEach(function (entry) {
        const phase = entry.step ? output.querySelector(`.phase[data-step="${entry.step}"]`) : null
        if (!phase) {
            output.querySelector('.phase-footer').append(logLine(entry))
            return
        }

        const body = phase.querySelector('.phase-body')
        // Dividers between attempts are only shown once the tracker has been retried
        if (body.dataset.attempt !== String(entry.attempt)) {
            body.dataset.attempt = String(entry.attempt)
            body.dataset.section = ''
            body.append(el('div', {className: 'attempt-divider', text: `Attempt ${entry.attempt}`}))
            if (entry.attempt > 1) {
                output.classList.add('multi-attempt')
            }
        }
        if (entry.section && body.dataset.section !== entry.section) {
            body.dataset.section = entry.section
            body.append(el('div', {className: 'phase-section', text: entry.section}))
        }
        body.append(logLine(entry))

        const wasEmpty = phase.dataset.count === '0'
        phase.dataset.count = String(Number(phase.dataset.count) + 1)
        if (entry.level === 'WARN' || entry.level === 'ERROR' || entry.level === 'FATAL') {
            phase.dataset.warnings = String(Number(phase.dataset.warnings) + 1)
        }
        if (wasEmpty && (phase.dataset.state === 'running' || phase.dataset.state === 'failed' || phase.dataset.state === 'cancelled')) {
            phase.open = true
        }
        updatePhaseCount(phase)
    })
}

function groupFor(state) {
    if (!groupElements[state]) {
        const definition = TRACKER_GROUPS.find(function (group) {
            return group.state === state
        })
        const title = el('h2')
        const list = el('div', {className: 'execution-list'})
        const previous = el('button', {className: 'button small', type: 'button', text: '▲', title: 'Previous section', 'aria-label': 'Previous section'})
        const next = el('button', {className: 'button small', type: 'button', text: '▼', title: 'Next section', 'aria-label': 'Next section'})
        const card = el('section', {className: `card execution-group group-${state.toLowerCase()}`}, [
            el('div', {className: 'card-header'}, [title, el('div', {className: 'group-nav'}, [previous, next])]),
            list,
        ])
        previous.addEventListener('click', function () {
            scrollToGroup(card.previousElementSibling)
        })
        next.addEventListener('click', function () {
            scrollToGroup(card.nextElementSibling)
        })
        groupElements[state] = {card: card, title: title, list: list, definition: definition, previous: previous, next: next}
    }
    return groupElements[state]
}

// Scrolls a group to just under the run card, which is pinned to the top of the page
function scrollToGroup(card) {
    if (!card) {
        return
    }
    const runCard = byId('run-card')
    const pinned = getComputedStyle(runCard).position === 'sticky'
    // The run card is condensed once the page is scrolled, so the group is placed under the condensed card
    if (pinned && window.scrollY + card.getBoundingClientRect().top > runCard.offsetTop) {
        updateRunCardCondensed(true)
    }
    const offset = pinned ? parseFloat(getComputedStyle(runCard).top) + runCard.offsetHeight + 12 : 72
    window.scrollTo({
        top: window.scrollY + card.getBoundingClientRect().top - offset,
        behavior: REDUCED_MOTION_QUERY.matches ? 'auto' : 'smooth',
    })
}

// Waiting trackers stay in execution order (the order of the status), running ones are in the order they started, and others by name
function sortForGroup(state, list) {
    if (state === 'WAITING') {
        return list
    }
    return list.slice().sort(function (first, second) {
        if (state === 'RUNNING') {
            return first.startedAt - second.startedAt
        }
        return first.name.localeCompare(second.name)
    })
}

export function renderExecutionTrackers(status) {
    const container = byId('execution-trackers')
    if (status.state === 'IDLE' && !trackersLoaded) {
        container.replaceChildren()
        return
    }
    const executionTrackers = status.state === 'IDLE' ? waitingTrackers(status.executionOrder) : status.trackers
    if (status.state === 'IDLE' && executionTrackers.length === 0) {
        const group = groupFor('WAITING')
        group.title.textContent = 'Waiting (0)'
        group.list.replaceChildren(el('div', {className: 'empty-group', text: 'No trackers enabled'}))
        container.replaceChildren(group.card)
        return
    }
    // While an execution is preparing its trackers, the rows already shown (such as the trackers waiting before it started) are kept
    if (executionTrackers.length === 0 && isActive(status) && trackerRows.size > 0) {
        return
    }
    if (executionTrackers.length === 0) {
        container.replaceChildren(el('div', {className: 'card empty-execution'}, [
            el('p', {className: 'muted', text: isActive(status) ? 'Preparing the trackers…' : 'No trackers were executed'}),
        ]))
        return
    }

    const finished = isFinished(status)
    const groups = []
    TRACKER_GROUPS.forEach(function (definition) {
        const inGroup = sortForGroup(definition.state, executionTrackers.filter(function (tracker) {
            return tracker.state === definition.state
        }))
        if (inGroup.length === 0) {
            return
        }

        const group = groupFor(definition.state)
        const title = finished && definition.titleWhenFinished ? definition.titleWhenFinished : definition.title
        group.title.textContent = `${title} (${inGroup.length})`
        inGroup.forEach(function (tracker) {
            let row = trackerRows.get(tracker.name)
            if (!row) {
                row = createExecutionRow(tracker)
                trackerRows.set(tracker.name, row)
            }
            updateExecutionRow(row, tracker)
            // Appending moves the row to the end of its (possibly new) group, keeping it expanded
            group.list.append(row)
        })
        // Rows of trackers no longer in the group (such as a tracker disabled before the first execution) are removed
        Array.from(group.list.children).forEach(function (child) {
            if (!inGroup.some(function (tracker) {
                return tracker.name === child.dataset.name
            })) {
                child.remove()
            }
        })
        groups.push(group)
    })
    container.replaceChildren.apply(container, groups.map(function (group) {
        return group.card
    }))
    groups.forEach(function (group, index) {
        group.previous.hidden = index === 0
        group.next.hidden = index === groups.length - 1
    })
}

// ---------------------------------------------------------------------------------------------------------------
// Main: logs (fetched only for expanded trackers)
// ---------------------------------------------------------------------------------------------------------------

// The ID of the last log message shown, and whether a request is in progress, per tracker
const logCursors = {}

function logLine(entry) {
    return el('div', {className: 'log-line', dataset: {level: entry.level}}, [
        el('span', {className: 'log-time', text: formatTime(entry.timestamp)}),
        el('span', {className: 'log-level', text: entry.level}),
        el('span', {className: 'log-message', text: entry.message}),
    ])
}

function fetchLogs(output) {
    const tracker = output.dataset.tracker
    const cursor = logCursors[tracker] || {lastId: 0, loading: false}
    logCursors[tracker] = cursor
    if (cursor.loading) {
        return
    }

    cursor.loading = true
    request('GET', `/api/logs?tracker=${encodeURIComponent(tracker)}&after=${cursor.lastId}`)
        .then(function (entries) {
            const placeholder = output.querySelector('.log-empty')
            if (entries.length === 0) {
                if (placeholder) {
                    placeholder.textContent = 'No log messages yet'
                }
                return
            }
            if (placeholder) {
                placeholder.remove()
            }

            // Only follow new messages if already scrolled to the bottom, so reading earlier messages is not interrupted
            const atBottom = output.scrollHeight - output.scrollTop - output.clientHeight < 24
            appendPhaseEntries(output, entries)
            cursor.lastId = entries[entries.length - 1].id
            if (atBottom) {
                output.scrollTop = output.scrollHeight
            }
        })
        .catch(function (error) {
            showToast(error.message, 'error')
        })
        .finally(function () {
            cursor.loading = false
        })
}

// Expanded logs are refreshed with each status update during an execution (and once more when it ends)
export function refreshOpenLogs(executionActive) {
    if (!executionActive || document.hidden) {
        return
    }
    document.querySelectorAll('details[open] > .log-output').forEach(fetchLogs)
}

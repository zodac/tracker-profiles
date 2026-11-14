import {MAXIMUM_LOG_LINES, TRACKER_GROUPS, TRACKER_STEPS} from './constants.mjs'
import {formatDuration, isActive, latestStatus} from './run.mjs'
import {updateTrackerScreenshots} from './screenshots.mjs'
import {currentSettingValue} from './settings.mjs'
import {byId, el, formatTime, pluralise, request, showToast} from './util.mjs'

// ---------------------------------------------------------------------------------------------------------------
// Main: trackers in the execution, grouped by state, each with its own logs
// ---------------------------------------------------------------------------------------------------------------

// Rows are kept between renders (and moved between groups), so an expanded tracker stays expanded, keyed by tracker name
let executionStartedAt = null
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
            } else if (step === nextStep) {
                state = tracker.state === 'RUNNING' ? 'current' : 'stopped'
            }
            return el('span', {className: `step ${state}`, title: step.label})
        }))
}

export function trackerTiming(tracker) {
    if (tracker.startedAt === null) {
        return ''
    }
    const end = tracker.finishedAt === null ? Date.now() : tracker.finishedAt
    return formatDuration(end - tracker.startedAt)
}

function trackerDetail(tracker) {
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

function createExecutionRow(tracker) {
    const summary = el('summary', {className: 'execution-row'})
    const screenshots = el('div', {className: 'tracker-screenshots', hidden: true})
    const logOutput = createPhaseLog(tracker)
    const row = el('details', {className: 'execution-tracker', dataset: {name: tracker.name}}, [summary, screenshots, logOutput])
    row.addEventListener('toggle', function () {
        if (row.open) {
            fetchLogs(logOutput)
        }
    })
    return row
}

function updateExecutionRow(row, tracker) {
    const summary = row.querySelector('summary')
    const needsInput = latestStatus && latestStatus.pendingInputs.some(function (input) {
        return input.trackerName === tracker.name
    })
    summary.replaceChildren(
        el('span', {className: 'execution-name'}, [
            tracker.name,
            tracker.type === 'MANUAL' ? el('span', {className: 'badge manual', text: 'Manual'}) : null,
            needsInput ? el('span', {className: 'badge needs-input', text: 'Needs you'}) : null,
        ]),
        stepBar(tracker),
        el('span', {className: 'execution-detail muted', text: trackerDetail(tracker), title: tracker.latestMessage}),
        el('span', {className: 'execution-time muted', text: trackerTiming(tracker)})
    )
    row.dataset.state = tracker.state
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
        const card = el('section', {className: `card execution-group group-${state.toLowerCase()}`}, [
            el('div', {className: 'card-header'}, [title]),
            list,
        ])
        groupElements[state] = {card: card, title: title, list: list, definition: definition}
    }
    return groupElements[state]
}

// Waiting trackers have no logs yet, so are shown compactly by name
function renderWaitingGroup(group, waiting) {
    group.list.replaceChildren(el('p', {className: 'waiting-names'}, waiting.map(function (tracker) {
        return el('span', {className: 'waiting-name'}, [
            tracker.name,
            tracker.type === 'MANUAL' ? el('span', {className: 'badge manual', text: 'Manual'}) : null,
        ])
    })))
}

function sortForGroup(state, list) {
    return list.slice().sort(function (first, second) {
        if (state === 'RUNNING') {
            return first.startedAt - second.startedAt
        }
        return first.name.localeCompare(second.name)
    })
}

export function renderExecutionTrackers(status) {
    const container = byId('execution-trackers')
    if (status.startedAt !== executionStartedAt) {
        executionStartedAt = status.startedAt
        trackerRows.clear()
        Object.keys(groupElements).forEach(function (state) {
            delete groupElements[state]
        })
        container.replaceChildren()
        byId('execution-log').querySelector('.log-output').replaceChildren()
        Object.keys(logCursors).forEach(function (tracker) {
            delete logCursors[tracker]
        })
    }

    // Nothing is shown until an execution has started
    if (status.state === 'IDLE') {
        container.replaceChildren()
        return
    }
    if (status.trackers.length === 0) {
        container.replaceChildren(el('div', {className: 'card empty-execution'}, [
            el('p', {className: 'muted', text: isActive(status) ? 'Preparing the trackers…' : 'No trackers were executed.'}),
        ]))
        return
    }

    const active = isActive(status)
    const groups = []
    TRACKER_GROUPS.forEach(function (definition) {
        const inGroup = sortForGroup(definition.state, status.trackers.filter(function (tracker) {
            return tracker.state === definition.state
        }))
        if (inGroup.length === 0) {
            return
        }

        const group = groupFor(definition.state)
        const title = !active && definition.titleWhenFinished ? definition.titleWhenFinished : definition.title
        group.title.textContent = `${title} (${inGroup.length})`
        if (definition.state === 'WAITING') {
            renderWaitingGroup(group, inGroup)
        } else {
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
        }
        groups.push(group.card)
    })
    container.replaceChildren.apply(container, groups)
}

// ---------------------------------------------------------------------------------------------------------------
// Main: logs (fetched only for expanded trackers)
// ---------------------------------------------------------------------------------------------------------------

// The ID of the last log message shown, and whether a request is in progress, per tracker ('' for the execution log)
const logCursors = {}

function logLine(entry) {
    return el('div', {className: 'log-line', dataset: {level: entry.level}}, [
        el('span', {className: 'log-time', text: formatTime(entry.timestamp)}),
        el('span', {className: 'log-level', text: entry.level}),
        el('span', {className: 'log-message', text: entry.message}),
    ])
}

export function fetchLogs(output) {
    const tracker = output.dataset.tracker
    const cursor = logCursors[tracker] || {lastId: 0, loading: false}
    logCursors[tracker] = cursor
    if (cursor.loading) {
        return
    }

    cursor.loading = true
    const query = tracker ? `tracker=${encodeURIComponent(tracker)}&after=${cursor.lastId}` : `after=${cursor.lastId}`
    request('GET', `/api/logs?${query}`)
        .then(function (entries) {
            const placeholder = output.querySelector('.log-empty')
            if (entries.length === 0) {
                if (placeholder) {
                    placeholder.textContent = 'No log messages yet'
                } else if (!output.dataset.tracker && output.childElementCount === 0) {
                    output.append(el('div', {className: 'log-empty', text: 'No log messages yet'}))
                }
                return
            }
            if (placeholder) {
                placeholder.remove()
            }

            // Only follow new messages if already scrolled to the bottom, so reading earlier messages is not interrupted
            const atBottom = output.scrollHeight - output.scrollTop - output.clientHeight < 24
            if (output.dataset.tracker) {
                appendPhaseEntries(output, entries)
            } else {
                const fragment = document.createDocumentFragment()
                entries.forEach(function (entry) {
                    fragment.append(logLine(entry))
                })
                output.append(fragment)
                while (output.childElementCount > MAXIMUM_LOG_LINES) {
                    output.firstElementChild.remove()
                }
            }
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

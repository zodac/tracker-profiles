(function () {
    'use strict'

    const MAXIMUM_LOG_LINES = 5000
    const MAXIMUM_TOASTS = 4
    const STATUS_POLL_INTERVAL_MS = 2000
    const IDLE_POLL_INTERVAL_MS = 10000
    const INPUT_POLL_INTERVAL_MS = 1000
    const TYPING_SAVE_DELAY_MS = 1000
    const THEME_STORAGE_KEY = 'tracker-profiles-theme'
    const SAVED_PASSWORD_PLACEHOLDER = '•••••••• (saved)'
    const PAGE_TITLE = document.title
    const DARK_SCHEME_QUERY = window.matchMedia('(prefers-color-scheme: dark)')
    const REDUCED_MOTION_QUERY = window.matchMedia('(prefers-reduced-motion: reduce)')
    const STATE_LABELS = {
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
    const TRACKER_STEPS = [
        {name: 'OPEN_TRACKER', label: 'Opening'},
        {name: 'LOGIN', label: 'Logging in'},
        {name: 'OPEN_PROFILE_PAGE', label: 'Opening profile'},
        {name: 'TAKE_SCREENSHOTS', label: 'Taking screenshots'},
        {name: 'LOGOUT', label: 'Logging out'},
    ]
    // The groups of trackers in an execution, in the order they are shown
    const TRACKER_GROUPS = [
        {state: 'RUNNING', title: 'In progress', empty: null},
        {state: 'WAITING', title: 'Waiting', titleWhenFinished: 'Not run'},
        {state: 'FAILED', title: 'Failed'},
        {state: 'CANCELLED', title: 'Cancelled'},
        {state: 'SUCCEEDED', title: 'Succeeded'},
    ]
    const OPTIONS_OPEN_STORAGE_KEY = 'tracker-profiles-run-options-open'
    const LOG_LEVEL_SETTING = 'LOG_LEVEL'
    const EXECUTION_ORDER_SETTING = 'TRACKER_EXECUTION_ORDER'
    const ADULT_TRACKERS_SETTING = 'ENABLE_ADULT_TRACKERS'

    // ---------------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------------

    function byId(id) {
        return document.getElementById(id)
    }

    function hasKey(object, key) {
        return Object.prototype.hasOwnProperty.call(object, key)
    }

    // Creates an element. Text is always set with textContent, so values from the server are never parsed as HTML
    function el(tag, options, children) {
        const element = document.createElement(tag)
        const opts = options || {}
        Object.keys(opts).forEach(function (key) {
            const value = opts[key]
            if (value === null || value === undefined || value === false) {
                return
            }
            if (key === 'className') {
                element.className = value
            } else if (key === 'text') {
                element.textContent = value
            } else if (key === 'dataset') {
                Object.assign(element.dataset, value)
            } else if (key.startsWith('on')) {
                element.addEventListener(key.substring(2), value)
            } else if (value === true) {
                element.setAttribute(key, '')
            } else {
                element.setAttribute(key, value)
            }
        })
        ;(children || []).forEach(function (child) {
            if (child !== null && child !== undefined) {
                element.append(child)
            }
        })
        return element
    }

    async function request(method, path, body) {
        const options = {method: method, headers: {}}
        if (body !== undefined) {
            options.headers['Content-Type'] = 'application/json'
            options.body = JSON.stringify(body)
        }

        const response = await fetch(path, options)
        const text = await response.text()
        const json = text ? JSON.parse(text) : null
        if (!response.ok) {
            throw new Error(json && json.error ? json.error : `Request failed with status ${response.status}`)
        }
        return json
    }

    function showToast(message, kind) {
        const isError = kind === 'error'
        const container = byId('toasts')
        const toast = el('div', {className: `toast${isError ? ' error' : ''}`, role: isError ? 'alert' : 'status'}, [
            el('span', {className: 'toast-icon', 'aria-hidden': 'true', text: isError ? '!' : '✓'}),
            el('span', {text: message}),
        ])
        container.append(toast)
        while (container.childElementCount > MAXIMUM_TOASTS) {
            container.firstElementChild.remove()
        }

        setTimeout(function () {
            toast.classList.add('leaving')
            setTimeout(function () {
                toast.remove()
            }, 300)
        }, isError ? 6000 : 2500)
    }

    function pluralise(count, word) {
        return `${count} ${word}${count === 1 ? '' : 's'}`
    }

    function formatTime(epochMillis) {
        const date = new Date(epochMillis)
        const pad = function (value, length) {
            return String(value).padStart(length, '0')
        }
        return `${pad(date.getHours(), 2)}:${pad(date.getMinutes(), 2)}:${pad(date.getSeconds(), 2)}.${pad(date.getMilliseconds(), 3)}`
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Theme (the saved choice is applied before the first paint by theme.js)
    // ---------------------------------------------------------------------------------------------------------------

    function currentTheme() {
        return document.documentElement.dataset.theme || (DARK_SCHEME_QUERY.matches ? 'dark' : 'light')
    }

    function updateThemeToggle() {
        const label = currentTheme() === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'
        const toggle = byId('theme-toggle')
        toggle.setAttribute('aria-label', label)
        toggle.title = label
    }

    function toggleTheme() {
        const theme = currentTheme() === 'dark' ? 'light' : 'dark'
        document.documentElement.dataset.theme = theme
        try {
            window.localStorage.setItem(THEME_STORAGE_KEY, theme)
        } catch {
            // Storage is unavailable, so the choice only applies until the page is closed
        }
        updateThemeToggle()
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tabs
    // ---------------------------------------------------------------------------------------------------------------

    const TAB_LOADERS = {
        main: function () {
            return loadSettings()
        },
        trackers: function () {
            return loadTrackers()
        },
        settings: function () {
            return loadSettings()
        },
    }

    function showTab() {
        const requested = window.location.hash.replace('#', '')
        const tabName = hasKey(TAB_LOADERS, requested) ? requested : 'main'

        document.querySelectorAll('.tab').forEach(function (tab) {
            tab.setAttribute('aria-selected', String(tab.dataset.tab === tabName))
        })
        document.querySelectorAll('.tab-panel').forEach(function (panel) {
            panel.hidden = panel.id !== `tab-${tabName}`
        })

        TAB_LOADERS[tabName]().catch(function (error) {
            showToast(error.message, 'error')
        })
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Main: execution status
    // ---------------------------------------------------------------------------------------------------------------

    let statusTimer = null
    let latestStatus = null

    function isActive(status) {
        return Boolean(status) && (status.state === 'RUNNING' || status.state === 'CANCELLING')
    }

    function formatDuration(milliseconds) {
        const totalSeconds = Math.max(0, Math.round(milliseconds / 1000))
        const minutes = Math.floor(totalSeconds / 60)
        const seconds = totalSeconds % 60
        return minutes > 0 ? `${minutes}m ${String(seconds).padStart(2, '0')}s` : `${seconds}s`
    }

    function formatTypeName(value) {
        const words = value.toLowerCase().replace(/_/g, ' ')
        return words.charAt(0).toUpperCase() + words.slice(1)
    }

    function enabledTrackersDescription() {
        if (!trackersLoaded) {
            return 'Takes a screenshot of the profile page of every enabled tracker.'
        }

        const enabled = trackers.filter(function (tracker) {
            return tracker.enabled
        })
        if (enabled.length === 0) {
            return 'No trackers are enabled yet. Enable them, or import a CSV file, on the Trackers tab.'
        }

        const manual = enabled.filter(function (tracker) {
            return tracker.type === 'MANUAL'
        }).length
        return `${pluralise(enabled.length, 'enabled tracker')}: ${enabled.length - manual} headless, ${manual} manual.`
    }

    function runDescription(status) {
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
        applyRunStateToOptions(status)
        refreshOpenLogs(active || wasActive)

        // The results of the trackers are shown on the Trackers tab, so are reloaded once an execution ends
        if (wasActive && !active && trackersLoaded) {
            loadTrackers().catch(function (error) {
                showToast(error.message, 'error')
            })
        }

        // While idle, the status is still checked occasionally, in case an execution is started from another browser
        clearTimeout(statusTimer)
        if (active) {
            statusTimer = setTimeout(refreshStatus, cancelling ? 500 : (waiting ? INPUT_POLL_INTERVAL_MS : STATUS_POLL_INTERVAL_MS))
        } else {
            statusTimer = setTimeout(refreshStatus, IDLE_POLL_INTERVAL_MS)
        }
    }

    function countTrackers(status, state) {
        return status.trackers.filter(function (tracker) {
            return tracker.state === state
        }).length
    }

    function refreshStatus() {
        return request('GET', '/api/run')
            .then(renderStatus)
            .catch(function (error) {
                showToast(error.message, 'error')
            })
    }

    function startRun() {
        byId('start-button').disabled = true
        request('POST', '/api/run', {})
            .then(renderStatus)
            .catch(function (error) {
                showToast(error.message, 'error')
                refreshStatus()
            })
    }

    function cancelRun() {
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
                if (action === 'extend') {
                    showToast(`Gave you 5 more minutes for ${input.trackerName}`)
                } else if (action === 'skip') {
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
        const timer = el('span', {className: 'input-timer', 'aria-label': 'Time remaining'})
        const extendButton = el('button', {className: 'button', type: 'button', text: '+5 min', title: 'Wait 5 more minutes before timing out'})
        const skipButton = el('button', {
            className: 'button', type: 'button', text: 'Skip tracker', title: 'Fail this attempt for the tracker, and move on',
        })
        const continueButton = el('button', {className: 'button primary', type: 'button', text: 'Continue'})
        extendButton.addEventListener('click', function () {
            respondToInput(input, 'extend', extendButton)
        })
        skipButton.addEventListener('click', function () {
            respondToInput(input, 'skip', skipButton)
        })
        continueButton.addEventListener('click', function () {
            respondToInput(input, 'continue', continueButton)
        })

        const card = el('div', {className: 'input-card', role: 'alert', dataset: {id: String(input.id)}}, [
            el('div', {className: 'input-text'}, [
                el('h2', {text: `${input.trackerName} needs you`}),
                el('p', {text: `${input.message} in the ${input.trackerName} browser window, then select Continue.`}),
                el('p', {className: 'muted', text: 'Skipping fails this attempt for the tracker. Continuing is automatic if the page changes.'}),
            ]),
            el('div', {className: 'input-actions'}, [timer, extendButton, skipButton, continueButton]),
        ])
        card.inputTimer = timer
        card.extendButton = extendButton
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
            }
            card.dataset.expiresAt = input.expiresAt === null ? '' : String(input.expiresAt)
            card.extendButton.hidden = input.expiresAt === null
        })
        updateInputTimers()
    }

    function updateInputTimers() {
        byId('pending-inputs').querySelectorAll('.input-card').forEach(function (card) {
            const expiresAt = card.dataset.expiresAt
            card.inputTimer.hidden = !expiresAt
            if (expiresAt) {
                card.inputTimer.textContent = formatDuration(Number(expiresAt) - Date.now())
            }
        })
    }

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

    function trackerTiming(tracker) {
        if (tracker.startedAt === null) {
            return ''
        }
        const end = tracker.finishedAt === null ? Date.now() : tracker.finishedAt
        return formatDuration(end - tracker.startedAt)
    }

    function trackerDetail(tracker) {
        const attempts = currentSettingValue('NUMBER_OF_SCREENSHOT_ATTEMPTS')
        // The total is left out if the number of attempts has been lowered below the current attempt during the execution
        const total = attempts && Number(attempts) >= tracker.attempt ? `/${attempts}` : ''
        const attempt = tracker.attempt > 1 ? `Attempt ${tracker.attempt}${total} · ` : ''
        if (tracker.state === 'RUNNING') {
            const nextStep = TRACKER_STEPS.find(function (step) {
                return !tracker.completedSteps.includes(step.name)
            })
            return `${attempt}${tracker.latestMessage || (nextStep ? nextStep.label : 'Finishing')}`
        }
        return `${attempt}${tracker.latestMessage}`
    }

    function createTrackerRow(tracker) {
        const summary = el('summary', {className: 'execution-row'})
        const logOutput = el('div', {className: 'log-output tracker-log', role: 'log', dataset: {tracker: tracker.name}},
            [el('div', {className: 'log-empty', text: 'Loading logs…'})])
        const row = el('details', {className: 'execution-tracker', dataset: {name: tracker.name}}, [summary, logOutput])
        row.addEventListener('toggle', function () {
            if (row.open) {
                fetchLogs(logOutput)
            }
        })
        return row
    }

    function updateTrackerRow(row, tracker) {
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

    function renderExecutionTrackers(status) {
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

        if (status.state === 'IDLE') {
            container.replaceChildren(el('div', {className: 'card empty-execution'}, [
                el('h2', {text: 'No execution yet'}),
                el('p', {className: 'muted', text: 'Press Start to screenshot every enabled tracker. Each tracker is listed here while it runs, with '
                    + 'its own logs, then grouped by whether it succeeded or failed.'}),
            ]))
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
                        row = createTrackerRow(tracker)
                        trackerRows.set(tracker.name, row)
                    }
                    updateTrackerRow(row, tracker)
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
            // The console output indents messages with tabs, which are too wide here
            el('span', {className: 'log-message', text: entry.message.replace(/^\t+/, function (tabs) {
                return '  '.repeat(tabs.length)
            })}),
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
        const query = tracker ? `tracker=${encodeURIComponent(tracker)}&after=${cursor.lastId}` : `after=${cursor.lastId}`
        request('GET', `/api/logs?${query}`)
            .then(function (entries) {
                const placeholder = output.querySelector('.log-empty')
                if (entries.length === 0) {
                    if (placeholder) {
                        placeholder.textContent = 'No log messages yet'
                    } else if (output.childElementCount === 0) {
                        output.append(el('div', {className: 'log-empty', text: 'No log messages yet'}))
                    }
                    return
                }
                if (placeholder) {
                    placeholder.remove()
                }

                // Only follow new messages if already scrolled to the bottom, so reading earlier messages is not interrupted
                const atBottom = output.scrollHeight - output.scrollTop - output.clientHeight < 24
                const fragment = document.createDocumentFragment()
                entries.forEach(function (entry) {
                    fragment.append(logLine(entry))
                })
                output.append(fragment)
                while (output.childElementCount > MAXIMUM_LOG_LINES) {
                    output.firstElementChild.remove()
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
    function refreshOpenLogs(executionActive) {
        if (!executionActive) {
            return
        }
        document.querySelectorAll('details[open] > .log-output').forEach(fetchLogs)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Trackers
    // ---------------------------------------------------------------------------------------------------------------

    let trackers = []
    let trackersLoaded = false
    // Input not yet saved, per tracker, so re-rendering (when searching or moving another tracker) does not lose it
    const trackerDrafts = {}

    function adultTrackersEnabled() {
        const setting = findSetting(ADULT_TRACKERS_SETTING)
        return !setting || setting.value.toLowerCase() === 'true'
    }

    function loadTrackers() {
        return request('GET', '/api/trackers').then(function (loaded) {
            trackers = loaded
            trackersLoaded = true
            renderTrackers()
            if (latestStatus) {
                byId('run-description').textContent = runDescription(latestStatus)
            }
        })
    }

    function findTracker(name) {
        return trackers.find(function (tracker) {
            return tracker.name === name
        })
    }

    function renderTrackers() {
        const search = byId('tracker-search').value.trim().toLowerCase()
        const visible = trackers.filter(function (tracker) {
            return !search || tracker.name.toLowerCase().includes(search)
        })
        const enabled = visible.filter(function (tracker) {
            return tracker.enabled
        })
        const disabled = visible.filter(function (tracker) {
            return !tracker.enabled
        })
        const headless = enabled.filter(function (tracker) {
            return tracker.type === 'HEADLESS'
        })
        const manual = enabled.filter(function (tracker) {
            return tracker.type === 'MANUAL'
        })

        const totalEnabled = trackers.filter(function (tracker) {
            return tracker.enabled
        }).length
        byId('tracker-summary').textContent = `${totalEnabled} of ${pluralise(trackers.length, 'supported tracker')} enabled`
        byId('adult-warning').hidden = adultTrackersEnabled() || !enabled.some(function (tracker) {
            return tracker.adult
        })

        const enabledGroup = el('details', {className: 'card group', open: true}, [
            el('summary', {}, [el('h2', {text: 'Enabled'}), el('span', {className: 'group-count', text: `(${enabled.length})`})]),
            el('div', {className: 'subgroup-title', text: `Headless (${headless.length})`}),
            renderTrackerRows(headless, 'No enabled headless trackers'),
            el('div', {className: 'subgroup-title', text: `Manual (${manual.length})`}),
            renderTrackerRows(manual, 'No enabled manual trackers'),
        ])
        const disabledGroup = el('details', {className: 'card group', open: true}, [
            el('summary', {}, [el('h2', {text: 'Disabled'}), el('span', {className: 'group-count', text: `(${disabled.length})`})]),
            renderTrackerRows(disabled, search ? 'No disabled trackers match the search' : 'All trackers are enabled'),
        ])

        byId('tracker-groups').replaceChildren(enabledGroup, disabledGroup)
    }

    function renderTrackerRows(list, emptyMessage) {
        if (list.length === 0) {
            return el('div', {className: 'empty-group', text: emptyMessage})
        }

        const fragment = document.createDocumentFragment()
        list.forEach(function (tracker) {
            fragment.append(renderTrackerRow(tracker))
        })
        return fragment
    }

    function formatLastRun(epochMillis) {
        const date = new Date(epochMillis)
        if (date.toDateString() === new Date().toDateString()) {
            return date.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})
        }
        return date.toLocaleDateString([], {day: 'numeric', month: 'short'})
    }

    function lastRunCell(tracker) {
        if (tracker.lastRunAt === null) {
            return el('span', {className: 'last-run muted', text: 'Never run'})
        }

        const succeeded = tracker.lastRunOk === true
        return el('span', {
            className: `last-run ${succeeded ? 'success' : 'failure'}`,
            title: `Last run ${succeeded ? 'succeeded' : 'failed'} at ${new Date(tracker.lastRunAt).toLocaleString()}`,
        }, [
            el('span', {'aria-hidden': 'true', text: succeeded ? '✓ ' : '✕ '}),
            el('span', {className: 'visually-hidden', text: succeeded ? 'Succeeded ' : 'Failed '}),
            formatLastRun(tracker.lastRunAt),
        ])
    }

    function importTrackers(file) {
        const importButton = byId('import-button')
        importButton.disabled = true
        file.text()
            .then(function (csv) {
                return request('POST', '/api/trackers/import', {csv: csv})
            })
            .then(function (result) {
                trackers = result.trackers
                renderTrackers()

                let message = `Imported ${file.name}: ${result.added} added, ${result.updated} updated.`
                if (result.unsupported.length > 0) {
                    message += ` Not imported, since they are not supported: ${result.unsupported.join(', ')}.`
                }
                const notice = byId('import-result')
                notice.textContent = message
                notice.hidden = false
                showToast(`Imported ${pluralise(result.added + result.updated, 'tracker')}`)
            })
            .catch(function (error) {
                showToast(`${file.name} was not imported: ${error.message}`, 'error')
            })
            .finally(function () {
                importButton.disabled = false
                byId('import-file').value = ''
            })
    }

    function trackerRowPositions() {
        const positions = new Map()
        document.querySelectorAll('.tracker-row').forEach(function (row) {
            positions.set(row.dataset.name, {row: row, top: row.getBoundingClientRect().top})
        })
        return positions
    }

    // Re-renders the trackers, with the moved tracker sliding from its old position into its new group (and the other rows sliding to make room)
    function renderTrackersWithMove(movedName) {
        const before = trackerRowPositions()
        renderTrackers()

        const after = trackerRowPositions()
        const moved = after.get(movedName)
        if (!moved) {
            return
        }
        moved.row.classList.add('just-moved')
        if (REDUCED_MOTION_QUERY.matches) {
            return
        }

        // Keep the moved tracker on screen, then measure again since scrolling changes the positions
        const bounds = moved.row.getBoundingClientRect()
        if (bounds.top < 60 || bounds.bottom > window.innerHeight) {
            moved.row.scrollIntoView({block: 'center'})
        }

        trackerRowPositions().forEach(function (position, name) {
            const previous = before.get(name)
            const offset = previous ? previous.top - position.top : 0
            if (offset === 0) {
                return
            }

            position.row.animate(
                [{transform: `translateY(${offset}px)`}, {transform: 'translateY(0)'}],
                {duration: name === movedName ? 700 : 350, easing: 'cubic-bezier(0.2, 0.8, 0.2, 1)'}
            )
        })
    }

    function renderTrackerRow(tracker) {
        let current = tracker
        // The values last saved from this row, used to decide whether there is anything new to save
        let saved = {username: tracker.username, password: ''}
        let typingTimer = null
        let saveQueue = Promise.resolve()

        const row = el('div', {className: 'tracker-row', dataset: {name: tracker.name}})
        const errorMessage = el('p', {className: 'tracker-error', hidden: true})
        const draft = trackerDrafts[tracker.name]

        const usernameInput = el('input', {
            type: 'text', placeholder: 'Username or email', autocomplete: 'off', 'aria-label': `${tracker.name} username`,
        })
        usernameInput.value = draft ? draft.username : tracker.username
        const passwordInput = el('input', {
            type: 'password', autocomplete: 'new-password', 'aria-label': `${tracker.name} password`,
        })
        passwordInput.value = draft ? draft.password : ''
        const enabledInput = el('input', {type: 'checkbox', role: 'switch', 'aria-label': `Enable ${tracker.name}`})
        enabledInput.checked = tracker.enabled
        const removeButton = el('button', {className: 'button danger small', type: 'button', text: 'Remove', title: 'Remove the saved credentials'})

        function hasUnsavedInput() {
            return usernameInput.value !== saved.username || passwordInput.value !== saved.password
        }

        function refreshRow() {
            passwordInput.placeholder = current.hasPassword ? SAVED_PASSWORD_PLACEHOLDER : 'Password'
            removeButton.hidden = !current.username && !current.hasPassword
            row.classList.toggle('editing', hasUnsavedInput())
            if (hasUnsavedInput()) {
                trackerDrafts[current.name] = {username: usernameInput.value, password: passwordInput.value}
            } else {
                delete trackerDrafts[current.name]
            }
        }

        function savedMessage(values, enabled) {
            if (enabled !== current.enabled) {
                return `${enabled ? 'Enabled' : 'Disabled'} ${current.name}`
            }
            const changed = []
            if (values.username !== saved.username) {
                changed.push('username')
            }
            if (values.password !== saved.password) {
                changed.push('password')
            }
            return `Saved ${changed.join(' and ') || 'credentials'} for ${current.name}`
        }

        function save(enabled) {
            const values = {username: usernameInput.value, password: passwordInput.value}
            errorMessage.hidden = true
            return request('PUT', '/api/trackers', {name: current.name, username: values.username, password: values.password, enabled: enabled})
                .then(function (updated) {
                    const message = savedMessage(values, enabled)
                    const moved = enabled !== current.enabled
                    trackers = updated
                    current = findTracker(current.name)
                    saved = values
                    refreshRow()
                    showToast(message)
                    if (moved) {
                        renderTrackersWithMove(current.name)
                    }
                })
                .catch(function (error) {
                    enabledInput.checked = current.enabled
                    errorMessage.textContent = error.message
                    errorMessage.hidden = false
                    showToast(error.message, 'error')
                })
        }

        // Saves are queued, so they always reach the server in the order they were made
        function queueSave(enabled) {
            clearTimeout(typingTimer)
            typingTimer = null
            saveQueue = saveQueue.then(function () {
                return save(enabled)
            })
        }

        function saveCredentialsIfChanged() {
            if (hasUnsavedInput()) {
                queueSave(current.enabled)
            }
        }

        function onTyping() {
            refreshRow()
            clearTimeout(typingTimer)
            typingTimer = setTimeout(saveCredentialsIfChanged, TYPING_SAVE_DELAY_MS)
        }

        ;[usernameInput, passwordInput].forEach(function (input) {
            input.addEventListener('input', onTyping)
            input.addEventListener('blur', function () {
                if (typingTimer !== null) {
                    saveCredentialsIfChanged()
                }
            })
            input.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') {
                    saveCredentialsIfChanged()
                }
            })
        })
        enabledInput.addEventListener('change', function () {
            queueSave(enabledInput.checked)
        })
        removeButton.addEventListener('click', function () {
            if (!window.confirm(`Remove the saved credentials for ${current.name}?`)) {
                return
            }
            const wasEnabled = current.enabled
            request('DELETE', `/api/trackers/${encodeURIComponent(current.name)}`)
                .then(function (updated) {
                    delete trackerDrafts[current.name]
                    trackers = updated
                    showToast(`Removed the credentials for ${current.name}`)
                    if (wasEnabled) {
                        renderTrackersWithMove(current.name)
                    } else {
                        renderTrackers()
                    }
                })
                .catch(function (error) {
                    showToast(error.message, 'error')
                })
        })

        const url = tracker.urls[0]
        const nameCell = el('div', {className: 'tracker-name'}, [
            el('a', {className: 'tracker-link', href: url, target: '_blank', rel: 'noopener noreferrer', title: `Open ${tracker.name} (${url})`}, [
                tracker.name,
                el('span', {className: 'external-icon', 'aria-hidden': 'true', text: '↗'}),
            ]),
            tracker.type === 'MANUAL' ? el('span', {className: 'badge manual', text: 'Manual', title: 'Requires user input (e.g. captcha)'}) : null,
            tracker.adult ? el('span', {className: 'badge adult', text: 'Adult'}) : null,
        ])
        const enabledCell = el('label', {className: 'switch', title: 'Enabled'}, [enabledInput, el('span', {className: 'switch-track'})])

        row.append(nameCell, usernameInput, passwordInput, lastRunCell(tracker), enabledCell, el('div', {className: 'tracker-actions'}, [removeButton]),
            errorMessage)
        refreshRow()
        return row
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Settings (shown as run options on the Main tab, or on the Settings tab)
    // ---------------------------------------------------------------------------------------------------------------

    let settings = []
    // Values changed in the UI but not yet saved (including any rejected by the server), keyed by setting name
    const unsavedSettings = {}
    const settingTimers = {}
    const settingRows = {}
    let settingsSaveQueue = Promise.resolve()

    function currentSettingValue(name) {
        const setting = findSetting(name)
        return setting ? setting.value : null
    }

    function findSetting(name) {
        return settings.find(function (setting) {
            return setting.name === name
        })
    }

    function hasPendingSettingChanges() {
        return Object.keys(unsavedSettings).length > 0 || Object.keys(settingTimers).length > 0
    }

    function loadSettings() {
        // Reloading replaces the inputs, so is skipped while a change is waiting to be saved
        if (hasPendingSettingChanges()) {
            return Promise.resolve()
        }

        return request('GET', '/api/settings').then(function (loaded) {
            settings = loaded
            renderSettings()
            renderRunOptions()
            if (latestStatus) {
                applyRunStateToOptions(latestStatus)
            }
            if (trackersLoaded) {
                renderTrackers()
            }
        })
    }

    function renderSettings() {
        const groups = []
        settings
            .filter(function (setting) {
                return setting.tab === 'SETTINGS'
            })
            .forEach(function (setting) {
                let group = groups.find(function (existing) {
                    return existing.name === setting.group
                })
                if (!group) {
                    group = {name: setting.group, settings: []}
                    groups.push(group)
                }
                group.settings.push(setting)
            })

        // Groups that cannot be changed in the UI (only set as environment variables) are shown last
        const isReadOnly = function (group) {
            return group.settings.every(function (setting) {
                return !setting.editable
            })
        }
        groups.sort(function (first, second) {
            return Number(isReadOnly(first)) - Number(isReadOnly(second))
        })

        const container = byId('settings-groups')
        container.replaceChildren.apply(container, groups.map(function (group) {
            return el('div', {className: 'card settings-group'}, [el('h2', {text: group.name})].concat(group.settings.map(renderSettingRow)))
        }))
    }

    function renderRunOptions() {
        const container = byId('run-options')
        container.replaceChildren.apply(container, settings
            .filter(function (setting) {
                return setting.tab === 'MAIN'
            })
            .map(renderOptionRow))
        renderRunOptionsSummary()
    }

    // A one-line summary of the main run options, shown when the run options are collapsed
    function renderRunOptionsSummary() {
        const order = currentSettingValue(EXECUTION_ORDER_SETTING)
        const attempts = currentSettingValue('NUMBER_OF_SCREENSHOT_ATTEMPTS')
        const parts = []
        if (order) {
            parts.push(order.split(',').map(function (type) {
                return formatTypeName(type.trim())
            }).join(' → '))
        }
        if (attempts) {
            parts.push(pluralise(Number(attempts), 'attempt'))
        }
        const logLevel = currentSettingValue(LOG_LEVEL_SETTING)
        if (logLevel) {
            parts.push(`${formatTypeName(logLevel)} logs`)
        }
        byId('run-options-summary').textContent = parts.join(' · ')
    }

    // Options read when an execution starts cannot change the execution in progress, so are locked until it ends
    function applyRunStateToOptions(status) {
        const active = isActive(status)
        settings.forEach(function (setting) {
            const row = settingRows[setting.name]
            if (!row || !row.setLocked || setting.tab !== 'MAIN') {
                return
            }
            row.setLocked(active && setting.scope !== 'LIVE')
        })

        const order = settingRows[EXECUTION_ORDER_SETTING]
        if (order && order.setFixedValues) {
            order.setFixedValues(active ? status.startedTrackerTypes : [])
        }
    }

    // An ordered selection of the allowed values: each can be switched on or off, and moved earlier. Values in 'fixed' (tracker types that have
    // already started) cannot be changed or moved
    function orderedChoiceInput(setting, onChange, label) {
        const container = el('div', {className: 'chips', role: 'group', 'aria-label': label})
        let order = setting.allowedValues.slice()
        let selected = new Set()
        let fixed = new Set()
        let disabled = !setting.editable

        function value() {
            return order.filter(function (item) {
                return selected.has(item)
            }).join(',')
        }

        function changed() {
            render()
            onChange(value(), true)
        }

        function render() {
            const focused = document.activeElement && container.contains(document.activeElement) ? document.activeElement.dataset : null
            container.replaceChildren.apply(container, order.map(function (item, index) {
                const isSelected = selected.has(item)
                const isFixed = fixed.has(item)
                const toggle = el('button', {
                    type: 'button',
                    className: `chip${isSelected ? ' selected' : ''}${isFixed ? ' fixed' : ''}`,
                    'aria-pressed': String(isSelected),
                    disabled: disabled || isFixed,
                    title: isFixed ? 'Already started, so cannot be changed' : `${isSelected ? 'Exclude' : 'Include'} ${formatTypeName(item)}`,
                    dataset: {item: item, control: 'toggle'},
                    text: formatTypeName(item),
                })
                toggle.addEventListener('click', function () {
                    if (isSelected && selected.size === 1) {
                        showToast(`${setting.label}: at least one must be selected`, 'error')
                        return
                    }
                    if (isSelected) {
                        selected.delete(item)
                    } else {
                        selected.add(item)
                    }
                    changed()
                })

                const children = [toggle]
                if (index > 0) {
                    const previous = order[index - 1]
                    const move = el('button', {
                        type: 'button',
                        className: 'chip-move',
                        'aria-label': `Move ${formatTypeName(item)} before ${formatTypeName(previous)}`,
                        title: `Move before ${formatTypeName(previous)}`,
                        disabled: disabled || isFixed || fixed.has(previous),
                        dataset: {item: item, control: 'move'},
                        text: '‹',
                    })
                    move.addEventListener('click', function () {
                        order = order.slice()
                        order[index - 1] = item
                        order[index] = previous
                        changed()
                    })
                    children.unshift(move)
                }
                return el('span', {className: 'chip-item'}, children)
            }))

            // Rendering replaces the buttons, so keep the keyboard focus on the same control
            if (focused) {
                const refocus = container.querySelector(`[data-item="${focused.item}"][data-control="${focused.control}"]`)
                    || container.querySelector(`[data-item="${focused.item}"]`)
                if (refocus && !refocus.disabled) {
                    refocus.focus()
                }
            }
        }

        return {
            element: container,
            setValue: function (newValue) {
                const values = newValue.split(',').map(function (item) {
                    return item.trim().toUpperCase()
                }).filter(Boolean)
                selected = new Set(values)
                order = values.concat(setting.allowedValues.filter(function (item) {
                    return !selected.has(item)
                }))
                render()
            },
            setDisabled: function (isDisabled) {
                disabled = isDisabled || !setting.editable
                render()
            },
            setFixedValues: function (values) {
                const updated = new Set(values)
                if (updated.size === fixed.size && values.every(function (item) {
                    return fixed.has(item)
                })) {
                    return
                }
                fixed = updated
                render()
            },
        }
    }

    // Creates the input for a setting. 'onChange' is called with the new value and whether to save it immediately (rather than after typing)
    function settingInput(setting, onChange, onFlush) {
        const disabled = !setting.editable
        const label = setting.label

        if (setting.type === 'BOOLEAN') {
            const checkbox = el('input', {type: 'checkbox', role: 'switch', disabled: disabled, 'aria-label': label})
            checkbox.addEventListener('change', function () {
                onChange(String(checkbox.checked), true)
            })
            return {
                element: el('label', {className: 'switch'}, [checkbox, el('span', {className: 'switch-track'})]),
                setValue: function (value) {
                    checkbox.checked = value.toLowerCase() === 'true'
                },
                setDisabled: function (isDisabled) {
                    checkbox.disabled = isDisabled || disabled
                },
            }
        }

        if (setting.type === 'SELECT') {
            const select = el('select', {disabled: disabled, 'aria-label': label}, setting.allowedValues.map(function (value) {
                return el('option', {value: value, text: value})
            }))
            select.addEventListener('change', function () {
                onChange(select.value, true)
            })
            return {
                element: select,
                setValue: function (value) {
                    // Values set as environment variables may not use the same case as the allowed values
                    select.value = value
                    if (select.value !== value) {
                        select.value = value.toUpperCase()
                    }
                },
                setDisabled: function (isDisabled) {
                    select.disabled = isDisabled || disabled
                },
            }
        }

        if (setting.type === 'MULTI_SELECT') {
            return orderedChoiceInput(setting, onChange, label)
        }

        const input = el('input', {
            type: setting.type === 'INTEGER' ? 'number' : 'text',
            min: setting.type === 'INTEGER' ? '0' : null,
            disabled: disabled,
            'aria-label': label,
        })
        input.addEventListener('input', function () {
            onChange(input.value, false)
        })
        input.addEventListener('blur', onFlush)
        input.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                onFlush()
            }
        })
        return {
            element: input,
            setValue: function (value) {
                input.value = value
            },
            setDisabled: function (isDisabled) {
                input.disabled = isDisabled || disabled
            },
        }
    }

    function createControl(setting) {
        const name = setting.name
        const control = settingInput(setting, function (value, immediate) {
            onSettingChanged(name, value, immediate)
        }, function () {
            if (settingTimers[name]) {
                queueSettingSave(name)
            }
        })
        control.setValue(setting.value)
        return control
    }

    function resetButton(name) {
        const current = findSetting(name)
        const resetTarget = current.environmentValue !== null ? 'environment value' : 'default'
        return el('button', {
            className: 'button small', type: 'button', text: 'Reset', title: `Reset to the ${resetTarget}`,
            onclick: function () {
                resetSetting(name)
            },
        })
    }

    // A run option on the Main tab
    function renderOptionRow(setting) {
        const name = setting.name
        const row = el('div', {className: `option-row${setting.type === 'MULTI_SELECT' ? ' wide' : ''}`, title: name})
        const lock = el('span', {className: 'lock-badge', text: 'Locked', title: 'Read when an execution starts, so locked until it finishes', hidden: true})
        const actions = el('div', {className: 'option-actions'})
        const errorMessage = el('p', {className: 'setting-error', hidden: true})
        const control = createControl(setting)

        function refresh() {
            const current = findSetting(name)
            row.classList.toggle('changed', current.changedFromDefault)
            row.classList.toggle('editing', hasKey(unsavedSettings, name))
            actions.replaceChildren()
            if (current.source === 'WEB_UI' && current.changedFromDefault) {
                actions.append(resetButton(name))
            }
        }

        function showError(message) {
            row.classList.toggle('invalid', Boolean(message))
            errorMessage.textContent = message || ''
            errorMessage.hidden = !message
        }

        settingRows[name] = {
            refresh: refresh,
            setValue: control.setValue,
            showError: showError,
            setLocked: function (locked) {
                lock.hidden = !locked
                row.classList.toggle('locked', locked)
                control.setDisabled(locked)
            },
            setFixedValues: control.setFixedValues,
        }
        refresh()

        row.append(
            el('div', {className: 'option-label'}, [
                setting.scope === 'LIVE' ? el('span', {className: 'live-dot', title: 'Can be changed during an execution'}) : null,
                el('span', {text: setting.label}),
                lock,
            ]),
            el('div', {className: 'option-control'}, [control.element, actions]),
            el('p', {className: 'option-description', text: setting.description}),
            errorMessage
        )
        return row
    }

    // A setting on the Settings tab
    function renderSettingRow(setting) {
        const name = setting.name
        const row = el('div', {className: 'setting-row'})
        const nameLine = el('div', {className: 'setting-name'})
        const meta = el('p', {className: 'setting-meta'})
        const actions = el('div', {className: 'setting-actions'})
        const errorMessage = el('p', {className: 'setting-error', hidden: true})
        const control = createControl(setting)

        // Updates the badges, default value and reset button, without replacing the input (which may have focus)
        function refresh() {
            const current = findSetting(name)
            row.classList.toggle('changed', current.changedFromDefault)
            row.classList.toggle('editing', hasKey(unsavedSettings, name))

            const badges = [el('strong', {className: 'setting-label', text: current.label}), el('code', {text: name})]
            if (current.changedFromDefault) {
                badges.push(el('span', {className: 'badge changed', text: 'Changed', title: `Default: ${current.defaultValue}`}))
            }
            if (current.source === 'ENVIRONMENT') {
                badges.push(el('span', {className: 'badge source-environment', text: 'Environment'}))
            } else if (current.source === 'WEB_UI') {
                badges.push(el('span', {className: 'badge source-web_ui', text: 'Saved'}))
            }
            if (!current.editable) {
                badges.push(el('span', {className: 'badge', text: 'Environment only', title: 'Read at startup; set as an environment variable'}))
            }
            nameLine.replaceChildren.apply(nameLine, badges)

            meta.replaceChildren(el('span', {text: 'Default: '}), el('span', {className: 'default-value', text: current.defaultValue || '(empty)'}))
            if (current.source === 'WEB_UI' && current.environmentValue !== null) {
                meta.append(el('span', {text: ` · Environment: ${current.environmentValue}`}))
            }

            actions.replaceChildren()
            if (current.source === 'WEB_UI') {
                actions.append(resetButton(name))
            }
        }

        function showError(message) {
            row.classList.toggle('invalid', Boolean(message))
            errorMessage.textContent = message || ''
            errorMessage.hidden = !message
        }

        settingRows[name] = {refresh: refresh, setValue: control.setValue, showError: showError}
        refresh()

        row.append(
            el('div', {}, [nameLine, el('p', {className: 'setting-description', text: setting.description}), meta]),
            el('div', {className: 'setting-input'}, [control.element, errorMessage]),
            actions
        )
        return row
    }

    function onSettingChanged(name, value, immediate) {
        clearTimeout(settingTimers[name])
        delete settingTimers[name]

        if (value === findSetting(name).value) {
            delete unsavedSettings[name]
            settingRows[name].showError(null)
            settingRows[name].refresh()
            return
        }

        unsavedSettings[name] = value
        settingRows[name].refresh()
        if (immediate) {
            queueSettingSave(name)
        } else {
            settingTimers[name] = setTimeout(function () {
                queueSettingSave(name)
            }, TYPING_SAVE_DELAY_MS)
        }
    }

    // Saves are queued, so they always reach the server in the order they were made
    function queueSettingSave(name) {
        clearTimeout(settingTimers[name])
        delete settingTimers[name]
        settingsSaveQueue = settingsSaveQueue.then(function () {
            return saveSetting(name)
        })
    }

    function putSettings(values) {
        return request('PUT', '/api/settings', {values: values}).then(function (updated) {
            return {values: values, updated: updated}
        })
    }

    function settingsSavedMessage(names) {
        const labels = names.map(function (savedName) {
            const setting = findSetting(savedName)
            return setting ? setting.label : savedName
        })
        const live = names.every(function (savedName) {
            const setting = findSetting(savedName)
            return setting && setting.scope === 'LIVE'
        })
        const suffix = isActive(latestStatus) ? (live ? ' (applied to this execution)' : ' (applies from the next execution)') : ''
        return `Saved ${labels.join(', ')}${suffix}`
    }

    // Saves the changed setting together with any other unsaved values, since some settings may only be valid together. If that fails, the
    // changed setting is saved on its own, so an invalid value elsewhere does not block it.
    function saveSetting(name) {
        if (!hasKey(unsavedSettings, name)) {
            return Promise.resolve()
        }

        const allUnsaved = Object.assign({}, unsavedSettings)
        const attempt = Object.keys(allUnsaved).length > 1
            ? putSettings(allUnsaved).catch(function () {
                return putSettings({[name]: allUnsaved[name]})
            })
            : putSettings(allUnsaved)

        return attempt
            .then(function (result) {
                settings = result.updated
                const savedNames = Object.keys(result.values)
                savedNames.forEach(function (savedName) {
                    if (unsavedSettings[savedName] === result.values[savedName]) {
                        delete unsavedSettings[savedName]
                    }
                    settingRows[savedName].showError(null)
                })
                Object.keys(settingRows).forEach(function (rowName) {
                    settingRows[rowName].refresh()
                })
                renderRunOptionsSummary()
                if (savedNames.includes(ADULT_TRACKERS_SETTING) && trackersLoaded) {
                    renderTrackers()
                }
                showToast(settingsSavedMessage(savedNames))
                if (latestStatus && savedNames.includes(EXECUTION_ORDER_SETTING)) {
                    refreshStatus()
                }
            })
            .catch(function (error) {
                const setting = findSetting(name)
                settingRows[name].showError(error.message)
                showToast(`${setting ? setting.label : name} was not saved: ${error.message}`, 'error')
            })
    }

    function resetSetting(name) {
        request('DELETE', `/api/settings/${encodeURIComponent(name)}`)
            .then(function (updated) {
                settings = updated
                delete unsavedSettings[name]
                settingRows[name].setValue(findSetting(name).value)
                settingRows[name].showError(null)
                Object.keys(settingRows).forEach(function (rowName) {
                    settingRows[rowName].refresh()
                })
                showToast(`Reset ${findSetting(name).label}`)
            })
            .catch(function (error) {
                showToast(error.message, 'error')
            })
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Start up
    // ---------------------------------------------------------------------------------------------------------------

    document.addEventListener('DOMContentLoaded', function () {
        updateThemeToggle()
        DARK_SCHEME_QUERY.addEventListener('change', updateThemeToggle)
        byId('theme-toggle').addEventListener('click', toggleTheme)
        byId('start-button').addEventListener('click', startRun)
        byId('cancel-button').addEventListener('click', cancelRun)
        const optionsPanel = byId('run-options-panel')
        try {
            optionsPanel.open = window.localStorage.getItem(OPTIONS_OPEN_STORAGE_KEY) === 'true'
        } catch {
            // Storage is unavailable, so the run options start collapsed
        }
        optionsPanel.addEventListener('toggle', function () {
            try {
                window.localStorage.setItem(OPTIONS_OPEN_STORAGE_KEY, String(optionsPanel.open))
            } catch {
                // Storage is unavailable, so the choice only applies until the page is closed
            }
        })
        const executionLog = byId('execution-log')
        executionLog.addEventListener('toggle', function () {
            if (executionLog.open) {
                fetchLogs(executionLog.querySelector('.log-output'))
            }
        })
        byId('tracker-search').addEventListener('input', renderTrackers)
        byId('import-button').addEventListener('click', function () {
            byId('import-file').click()
        })
        byId('import-file').addEventListener('change', function () {
            const file = byId('import-file').files[0]
            if (file) {
                importTrackers(file)
            }
        })
        window.addEventListener('hashchange', showTab)
        // Warn before leaving while a change is waiting to be saved, or was rejected
        window.addEventListener('beforeunload', function (event) {
            if (Object.keys(unsavedSettings).length > 0 || Object.keys(trackerDrafts).length > 0) {
                event.preventDefault()
            }
        })
        setInterval(function () {
            updateInputTimers()
            if (isActive(latestStatus)) {
                byId('run-description').textContent = runDescription(latestStatus)
                document.querySelectorAll('details.execution-tracker[data-state="RUNNING"] .execution-time').forEach(function (time) {
                    const tracker = latestStatus.trackers.find(function (candidate) {
                        return candidate.name === time.closest('details').dataset.name
                    })
                    if (tracker) {
                        time.textContent = trackerTiming(tracker)
                    }
                })
            }
        }, 1000)

        showTab()
        refreshStatus()
        loadTrackers().catch(function (error) {
            showToast(error.message, 'error')
        })
    })
}())

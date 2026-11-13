(function () {
    'use strict'

    const LOG_LEVEL_ORDER = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL']
    const MAXIMUM_LOG_LINES = 5000
    const MAXIMUM_TOASTS = 4
    const STATUS_POLL_INTERVAL_MS = 2000
    const TYPING_SAVE_DELAY_MS = 1000
    const THEME_STORAGE_KEY = 'tracker-profiles-theme'
    const SAVED_PASSWORD_PLACEHOLDER = '•••••••• (saved)'
    const DARK_SCHEME_QUERY = window.matchMedia('(prefers-color-scheme: dark)')
    const REDUCED_MOTION_QUERY = window.matchMedia('(prefers-reduced-motion: reduce)')
    const STATE_LABELS = {
        IDLE: 'Idle',
        RUNNING: 'Running',
        CANCELLING: 'Cancelling',
        CANCELLED: 'Cancelled',
        SUCCESS: 'Succeeded',
        PARTIAL_FAILURE: 'Partially failed',
        FAILURE: 'Failed',
    }

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
            return Promise.resolve()
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

    function renderStatus(status) {
        const running = status.state === 'RUNNING'
        const cancelling = status.state === 'CANCELLING'
        const active = running || cancelling
        const headerStatus = byId('header-status')
        headerStatus.dataset.state = status.state
        headerStatus.textContent = STATE_LABELS[status.state] || status.state

        const startButton = byId('start-button')
        startButton.disabled = active
        startButton.textContent = active ? 'Running…' : 'Start'

        const cancelButton = byId('cancel-button')
        cancelButton.hidden = !active
        cancelButton.disabled = cancelling
        cancelButton.textContent = cancelling ? 'Cancelling…' : 'Cancel'

        const description = byId('run-description')
        if (cancelling) {
            description.textContent = 'Cancelling: closing all browsers and stopping in-progress trackers…'
        } else if (running) {
            description.textContent = `Started at ${new Date(status.startedAt).toLocaleTimeString()}`
        } else if (status.state === 'CANCELLED' && status.finishedAt) {
            description.textContent = `Last execution cancelled at ${new Date(status.finishedAt).toLocaleTimeString()}`
        } else if (status.finishedAt) {
            const seconds = Math.round((status.finishedAt - status.startedAt) / 1000)
            description.textContent = `Last execution ${(STATE_LABELS[status.state] || '').toLowerCase()} at `
                + `${new Date(status.finishedAt).toLocaleTimeString()} (took ${seconds}s)`
        } else {
            description.textContent = 'Takes a screenshot of the profile page of every enabled tracker.'
        }

        const completed = status.successfulTrackers.length + status.failedTrackers.length
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
        byId('success-count').textContent = String(status.successfulTrackers.length)
        byId('failure-count').textContent = String(status.failedTrackers.length)

        const failedTrackers = byId('failed-trackers')
        failedTrackers.hidden = status.failedTrackers.length === 0
        failedTrackers.textContent = `Failed: ${status.failedTrackers.join(', ')}`

        clearTimeout(statusTimer)
        statusTimer = active ? setTimeout(refreshStatus, cancelling ? 500 : STATUS_POLL_INTERVAL_MS) : null
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
    // Main: logs
    // ---------------------------------------------------------------------------------------------------------------

    function levelIsVisible(level, minimumLevel) {
        return LOG_LEVEL_ORDER.indexOf(level) >= LOG_LEVEL_ORDER.indexOf(minimumLevel)
    }

    function applyLogFilter() {
        const minimumLevel = byId('log-level-filter').value
        byId('log-output').querySelectorAll('.log-line').forEach(function (line) {
            line.hidden = !levelIsVisible(line.dataset.level, minimumLevel)
        })
        scrollLogsIfNeeded()
    }

    function scrollLogsIfNeeded() {
        if (byId('log-autoscroll').checked) {
            const output = byId('log-output')
            output.scrollTop = output.scrollHeight
        }
    }

    function appendLogEntries(entries) {
        const output = byId('log-output')
        const placeholder = output.querySelector('.log-empty')
        if (placeholder) {
            placeholder.remove()
        }

        const minimumLevel = byId('log-level-filter').value
        const fragment = document.createDocumentFragment()
        entries.forEach(function (entry) {
            const line = el('div', {className: 'log-line', dataset: {level: entry.level}}, [
                el('span', {className: 'log-time', text: formatTime(entry.timestamp)}),
                el('span', {className: 'log-level', text: entry.level}),
                el('span', {className: 'log-message'}, [
                    entry.tracker ? el('span', {className: 'log-tracker', text: entry.tracker}) : null,
                    entry.message,
                ]),
            ])
            line.hidden = !levelIsVisible(entry.level, minimumLevel)
            fragment.append(line)
        })
        output.append(fragment)

        while (output.childElementCount > MAXIMUM_LOG_LINES) {
            output.firstElementChild.remove()
        }
        scrollLogsIfNeeded()
    }

    function connectLogs() {
        const connection = byId('log-connection')
        const source = new EventSource('/api/logs')
        let pending = []
        let flushScheduled = false

        // Batch rendering, since the retained log history arrives as many events at once
        function flush() {
            flushScheduled = false
            const batch = pending
            pending = []
            appendLogEntries(batch)
        }

        source.onopen = function () {
            connection.dataset.connected = 'true'
            connection.textContent = 'Live'
        }
        source.onerror = function () {
            connection.dataset.connected = 'false'
            connection.textContent = 'Reconnecting'
        }
        source.onmessage = function (event) {
            pending.push(JSON.parse(event.data))
            if (!flushScheduled) {
                flushScheduled = true
                requestAnimationFrame(flush)
            }
            // A new log message usually means the status has changed, so refresh it if an execution is not being polled
            if (statusTimer === null) {
                statusTimer = setTimeout(refreshStatus, 500)
            }
        }
    }

    function clearLogs() {
        byId('log-output').replaceChildren(el('div', {className: 'log-empty', text: 'Waiting for log messages…'}))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Trackers
    // ---------------------------------------------------------------------------------------------------------------

    let trackers = []
    let adultTrackersEnabled = true
    // Input not yet saved, per tracker, so re-rendering (when searching or moving another tracker) does not lose it
    const trackerDrafts = {}

    function loadTrackers() {
        return Promise.all([request('GET', '/api/trackers'), request('GET', '/api/settings')])
            .then(function (results) {
                trackers = results[0]
                const adultSetting = results[1].find(function (setting) {
                    return setting.name === 'ENABLE_ADULT_TRACKERS'
                })
                adultTrackersEnabled = !adultSetting || adultSetting.value.toLowerCase() === 'true'
                renderTrackers()
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
        byId('adult-warning').hidden = adultTrackersEnabled || !enabled.some(function (tracker) {
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

        row.append(nameCell, usernameInput, passwordInput, enabledCell, el('div', {className: 'tracker-actions'}, [removeButton]), errorMessage)
        refreshRow()
        return row
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------------------------------------------------------

    let settings = []
    // Values changed in the UI but not yet saved (including any rejected by the server), keyed by setting name
    const unsavedSettings = {}
    const settingTimers = {}
    const settingRows = {}
    let settingsSaveQueue = Promise.resolve()

    function findSetting(name) {
        return settings.find(function (setting) {
            return setting.name === name
        })
    }

    function loadSettings() {
        return request('GET', '/api/settings').then(function (loaded) {
            settings = loaded
            Object.keys(unsavedSettings).forEach(function (name) {
                delete unsavedSettings[name]
            })
            renderSettings()
        })
    }

    function renderSettings() {
        const groups = []
        settings.forEach(function (setting) {
            let group = groups.find(function (existing) {
                return existing.name === setting.group
            })
            if (!group) {
                group = {name: setting.group, settings: []}
                groups.push(group)
            }
            group.settings.push(setting)
        })

        const container = byId('settings-groups')
        container.replaceChildren.apply(container, groups.map(function (group) {
            return el('div', {className: 'card settings-group'}, [el('h2', {text: group.name})].concat(group.settings.map(renderSettingRow)))
        }))
    }

    // Creates the input for a setting. 'onChange' is called with the new value and whether to save it immediately (rather than after typing)
    function settingInput(setting, onChange, onFlush) {
        const disabled = !setting.editable
        const label = `${setting.name} value`

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
                    select.value = value.toUpperCase()
                },
            }
        }

        const input = el('input', {
            type: setting.type === 'INTEGER' ? 'number' : 'text',
            disabled: disabled,
            maxlength: setting.type === 'CHARACTER' ? '2' : null,
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

        const setValue = function (value) {
            input.value = value
        }
        if (setting.type === 'MULTI_SELECT') {
            return {
                element: el('div', {className: 'setting-input'}, [
                    input,
                    el('span', {className: 'muted', text: `Comma-separated, in order. Allowed: ${setting.allowedValues.join(', ')}`}),
                ]),
                setValue: setValue,
            }
        }
        return {element: input, setValue: setValue}
    }

    function renderSettingRow(setting) {
        const name = setting.name
        const row = el('div', {className: 'setting-row'})
        const nameLine = el('div', {className: 'setting-name'})
        const meta = el('p', {className: 'setting-meta'})
        const actions = el('div', {className: 'setting-actions'})
        const errorMessage = el('p', {className: 'setting-error', hidden: true})

        const control = settingInput(setting, function (value, immediate) {
            onSettingChanged(name, value, immediate)
        }, function () {
            if (settingTimers[name]) {
                queueSettingSave(name)
            }
        })
        control.setValue(setting.value)

        // Updates the badges, default value and reset button, without replacing the input (which may have focus)
        function refresh() {
            const current = findSetting(name)
            row.classList.toggle('changed', current.changedFromDefault)
            row.classList.toggle('editing', hasKey(unsavedSettings, name))

            const badges = [el('code', {text: name})]
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
                const resetTarget = current.environmentValue !== null ? 'environment value' : 'default'
                actions.append(el('button', {
                    className: 'button small', type: 'button', text: 'Reset', title: `Reset to the ${resetTarget}`,
                    onclick: function () {
                        resetSetting(name)
                    },
                }))
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

    // Saves the changed setting together with any other unsaved values, since some settings are only valid together (e.g. the browser width
    // and height). If that fails, the changed setting is saved on its own, so an invalid value elsewhere does not block it.
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
                showToast(`Saved ${savedNames.join(', ')}`)
            })
            .catch(function (error) {
                settingRows[name].showError(error.message)
                showToast(`${name} was not saved: ${error.message}`, 'error')
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
                showToast(`Reset ${name}`)
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
        byId('log-level-filter').addEventListener('change', applyLogFilter)
        byId('log-autoscroll').addEventListener('change', scrollLogsIfNeeded)
        byId('log-clear').addEventListener('click', clearLogs)
        byId('tracker-search').addEventListener('input', renderTrackers)
        window.addEventListener('hashchange', showTab)
        // Warn before leaving while a change is waiting to be saved, or was rejected
        window.addEventListener('beforeunload', function (event) {
            if (Object.keys(unsavedSettings).length > 0 || Object.keys(trackerDrafts).length > 0) {
                event.preventDefault()
            }
        })

        clearLogs()
        showTab()
        refreshStatus()
        connectLogs()
    })
}())

(function () {
    'use strict'

    const MAXIMUM_LOG_LINES = 5000
    const MAXIMUM_TOASTS = 4
    const STATUS_POLL_INTERVAL_MS = 2000
    const IDLE_POLL_INTERVAL_MS = 10000
    const INPUT_POLL_INTERVAL_MS = 1000
    const TYPING_SAVE_DELAY_MS = 1000
    const THEME_STORAGE_KEY = 'tracker-profiles-theme'
    // Shown (masked) in the password field of a tracker with a saved password, which is never sent to the browser. Private-use characters, so it
    // cannot match a password that is typed
    const SAVED_PASSWORD_VALUE = '\uE000'.repeat(8)
    // How long a tracker whose credentials have just been set stays in its group, while not being changed, before moving
    const UNSET_MOVE_DELAY_MS = 10000
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
        {name: 'OPEN_TRACKER', label: 'Opening tracker'},
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
    const LOG_LEVEL_SETTING = 'LOG_LEVEL'
    const EXECUTION_ORDER_SETTING = 'TRACKER_EXECUTION_ORDER'
    const ADULT_TRACKERS_SETTING = 'ENABLE_ADULT_TRACKERS'
    const INPUT_TIMEOUT_SETTING = 'INPUT_TIMEOUT_SECONDS'
    // The timeout used when the input timeout is switched on, unless another was set before
    const DEFAULT_INPUT_TIMEOUT_SECONDS = 300

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
        // The run options are open until an execution starts (or if one is already running when the page is opened)
        if (latestStatus === null) {
            byId('run-options-panel').open = !isActive(status)
        }
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
        byId('run-options-panel').open = false
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
    // Main: screenshots of each tracker (thumbnails, opened in a gallery), and downloads
    // ---------------------------------------------------------------------------------------------------------------

    // The screenshots shown in the gallery, and the index of the one being viewed
    const gallery = {trackerName: null, screenshots: [], index: 0}

    // 'savedAt' is included so a screenshot replaced on a retry is fetched again (otherwise the image is cached by the browser)
    function screenshotUrl(trackerName, screenshot, option) {
        const parameters = new URLSearchParams({tracker: trackerName, id: screenshot.id, v: String(screenshot.savedAt)})
        if (option) {
            parameters.set(option, 'true')
        }
        return `/api/screenshots?${parameters}`
    }

    function trackerDownloadUrl(tracker) {
        return `/api/screenshots?${new URLSearchParams({tracker: tracker.name})}`
    }

    function downloadLink(href, text, title, small) {
        return el('a', {className: `button${small ? ' small' : ''} download-link`, href: href, download: '', title: title}, [
            el('span', {className: 'download-icon', 'aria-hidden': 'true'}),
            text,
        ])
    }

    // Only re-rendered when the screenshots change, so a thumbnail is not fetched again with each status update
    function updateTrackerScreenshots(container, tracker) {
        const signature = tracker.screenshots.map(function (screenshot) {
            return `${screenshot.id}:${screenshot.savedAt}`
        }).join(',')
        if (container.dataset.signature === signature) {
            return
        }
        container.dataset.signature = signature
        container.hidden = tracker.screenshots.length === 0
        if (tracker.screenshots.length === 0) {
            container.replaceChildren()
            return
        }

        const multiple = tracker.screenshots.length > 1
        container.replaceChildren(
            el('div', {className: 'screenshots-header'}, [
                el('h3', {text: multiple ? `Screenshots (${tracker.screenshots.length})` : 'Screenshot'}),
                downloadLink(trackerDownloadUrl(tracker), multiple ? 'Download all' : 'Download',
                    multiple ? `Download the screenshots of ${tracker.name} as a .zip file` : `Download the screenshot of ${tracker.name}`, true),
            ]),
            el('div', {className: 'screenshot-list'}, tracker.screenshots.map(function (screenshot, index) {
                const label = screenshot.failure ? 'Failure screenshot' : screenshot.label
                return el('figure', {className: `screenshot-card${screenshot.failure ? ' failure' : ''}`}, [
                    el('button', {
                        className: 'screenshot-thumbnail',
                        type: 'button',
                        title: `View ${label.toLowerCase()}`,
                        onclick: function () {
                            openGallery(tracker.name, tracker.screenshots, index)
                        },
                    }, [
                        el('img', {src: screenshotUrl(tracker.name, screenshot, 'thumbnail'), alt: `${tracker.name}: ${label}`, loading: 'lazy'}),
                    ]),
                    el('figcaption', {}, [
                        el('span', {className: 'screenshot-label', text: label}),
                        el('a', {
                            className: 'icon-button small download-link',
                            href: screenshotUrl(tracker.name, screenshot, 'download'),
                            download: screenshot.fileName,
                            title: `Download ${screenshot.fileName}`,
                            'aria-label': `Download ${screenshot.fileName}`,
                        }, [el('span', {className: 'download-icon', 'aria-hidden': 'true'})]),
                    ]),
                ])
            }))
        )
    }

    // The profile screenshots of every successful tracker can be downloaded together
    function updateExecutionDownload(status) {
        const successful = status.trackers.filter(function (tracker) {
            return tracker.state === 'SUCCEEDED' && tracker.screenshots.length > 0
        })
        const count = successful.reduce(function (total, tracker) {
            return total + tracker.screenshots.length
        }, 0)
        const link = byId('download-screenshots')
        link.hidden = count === 0
        link.querySelector('.download-text').textContent = count > 1 ? `Download all (${count})` : 'Download screenshot'
        link.title = count > 1
            ? `Download the ${count} screenshots of the ${pluralise(successful.length, 'successful tracker')} as a .zip file`
            : 'Download the screenshot of the successful tracker'
    }

    function openGallery(trackerName, screenshots, index) {
        gallery.trackerName = trackerName
        gallery.screenshots = screenshots
        const dialog = byId('screenshot-gallery')
        byId('gallery-title').textContent = trackerName
        const multiple = screenshots.length > 1
        byId('gallery-previous').hidden = !multiple
        byId('gallery-next').hidden = !multiple
        byId('gallery-tabs').replaceChildren.apply(byId('gallery-tabs'), multiple ? screenshots.map(function (screenshot, tabIndex) {
            return el('button', {
                className: 'gallery-tab',
                type: 'button',
                role: 'tab',
                text: screenshot.failure ? 'Failure' : screenshot.label,
                onclick: function () {
                    showGalleryScreenshot(tabIndex)
                },
            })
        }) : [])
        showGalleryScreenshot(index)
        if (!dialog.open) {
            dialog.showModal()
        }
    }

    function showGalleryScreenshot(index) {
        const count = gallery.screenshots.length
        gallery.index = (index + count) % count
        const screenshot = gallery.screenshots[gallery.index]
        const label = screenshot.failure ? 'Failure screenshot' : screenshot.label

        byId('gallery-counter').textContent = count > 1 ? `${label} · ${gallery.index + 1} of ${count}` : label
        byId('gallery-tabs').querySelectorAll('.gallery-tab').forEach(function (tab, tabIndex) {
            tab.setAttribute('aria-selected', String(tabIndex === gallery.index))
        })
        const fullSize = byId('gallery-full-size')
        fullSize.href = screenshotUrl(gallery.trackerName, screenshot)
        fullSize.title = `Open ${screenshot.fileName} at full size in a new tab`
        const download = byId('gallery-download')
        download.href = screenshotUrl(gallery.trackerName, screenshot, 'download')
        download.download = screenshot.fileName
        download.title = `Download ${screenshot.fileName}`

        const viewport = byId('gallery-viewport')
        viewport.classList.add('loading')
        viewport.scrollTop = 0
        const image = el('img', {className: 'gallery-image', alt: `${gallery.trackerName}: ${label}`})
        image.addEventListener('load', function () {
            viewport.classList.remove('loading')
        })
        image.addEventListener('error', function () {
            viewport.classList.remove('loading')
            viewport.replaceChildren(el('p', {className: 'gallery-error', text: 'The screenshot could not be loaded.'}))
        })
        // Scaled down to 1080p, since a full-size screenshot at a high resolution can be too large for the browser to show
        image.src = screenshotUrl(gallery.trackerName, screenshot, 'preview')
        viewport.replaceChildren(image)
    }

    function setUpGallery() {
        const dialog = byId('screenshot-gallery')
        byId('gallery-close').addEventListener('click', function () {
            dialog.close()
        })
        byId('gallery-previous').addEventListener('click', function () {
            showGalleryScreenshot(gallery.index - 1)
        })
        byId('gallery-next').addEventListener('click', function () {
            showGalleryScreenshot(gallery.index + 1)
        })
        // Clicking the backdrop (outside the content) closes the gallery
        dialog.addEventListener('click', function (event) {
            if (event.target === dialog) {
                dialog.close()
            }
        })
        dialog.addEventListener('keydown', function (event) {
            if (gallery.screenshots.length < 2) {
                return
            }
            if (event.key === 'ArrowLeft') {
                event.preventDefault()
                showGalleryScreenshot(gallery.index - 1)
            } else if (event.key === 'ArrowRight') {
                event.preventDefault()
                showGalleryScreenshot(gallery.index + 1)
            }
        })
        dialog.addEventListener('close', function () {
            byId('gallery-viewport').replaceChildren()
        })
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
    // Input not yet saved, per tracker, so a warning is shown before leaving the page
    const trackerDrafts = {}
    // Rows are kept between renders (and moved between groups), so a focused input is never replaced, keyed by tracker name
    const trackerRowsByName = new Map()
    // Trackers kept in the group they were shown in after their credentials changed, until the user clicks elsewhere or stops changing them
    const pinnedTrackers = new Map()
    let trackerGroups = null

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

    function hasCredentials(tracker) {
        return Boolean(tracker.username) && tracker.hasPassword
    }

    // The group a tracker belongs in: 'headless' or 'manual' (enabled), 'disabled', or 'unset' (no username or password)
    function trackerGroupOf(tracker) {
        if (!hasCredentials(tracker)) {
            return 'unset'
        }
        if (!tracker.enabled) {
            return 'disabled'
        }
        return tracker.type === 'MANUAL' ? 'manual' : 'headless'
    }

    function displayedGroupOf(tracker) {
        const pinned = pinnedTrackers.get(tracker.name)
        return pinned ? pinned.group : trackerGroupOf(tracker)
    }

    function pinTracker(name, group) {
        const existing = pinnedTrackers.get(name)
        const pinned = existing || {group: group, timer: null}
        clearTimeout(pinned.timer)
        pinned.timer = setTimeout(function () {
            releaseTracker(name)
        }, UNSET_MOVE_DELAY_MS)
        pinnedTrackers.set(name, pinned)
    }

    // Restarts the delay before a pinned tracker moves, since its credentials are still being changed
    function touchPinnedTracker(name) {
        if (pinnedTrackers.has(name)) {
            pinTracker(name)
        }
    }

    function unpinTracker(name) {
        const pinned = pinnedTrackers.get(name)
        if (pinned) {
            clearTimeout(pinned.timer)
            pinnedTrackers.delete(name)
        }
    }

    function releaseTracker(name) {
        const pinned = pinnedTrackers.get(name)
        if (!pinned) {
            return
        }
        unpinTracker(name)
        const tracker = findTracker(name)
        if (tracker && trackerGroupOf(tracker) !== pinned.group) {
            renderTrackersWithMove(name)
        }
    }

    // A pinned tracker moves once the user clicks anything outside its row. The move waits until the click has focused its target, so the
    // focus is not lost
    function releasePinnedTrackersOutside(target) {
        pinnedTrackers.forEach(function (pinned, name) {
            const row = trackerRowsByName.get(name)
            if (!row || !row.element.contains(target)) {
                setTimeout(function () {
                    releaseTracker(name)
                }, 0)
            }
        })
    }

    function createTrackerGroup(key, emptyMessage) {
        return {key: key, list: el('div', {className: 'tracker-list'}), empty: el('div', {className: 'empty-group', text: emptyMessage}), title: null}
    }

    function buildTrackerGroups() {
        const groups = {
            headless: createTrackerGroup('headless', 'No enabled headless trackers'),
            manual: createTrackerGroup('manual', 'No enabled manual trackers'),
            disabled: createTrackerGroup('disabled', ''),
            unset: createTrackerGroup('unset', ''),
        }
        groups.headless.title = el('div', {className: 'subgroup-title'})
        groups.manual.title = el('div', {className: 'subgroup-title'})
        const counts = {
            enabled: el('span', {className: 'group-count'}),
            disabled: el('span', {className: 'group-count'}),
            unset: el('span', {className: 'group-count'}),
        }

        const enabledCard = el('details', {className: 'card group', open: true}, [
            el('summary', {}, [el('h2', {text: 'Enabled'}), counts.enabled]),
            groups.headless.title, groups.headless.list, groups.headless.empty,
            groups.manual.title, groups.manual.list, groups.manual.empty,
        ])
        const disabledCard = el('details', {className: 'card group', open: true}, [
            el('summary', {}, [el('h2', {text: 'Disabled'}), counts.disabled]),
            groups.disabled.list, groups.disabled.empty,
        ])
        const unsetCard = el('details', {className: 'card group', open: true}, [
            el('summary', {}, [
                el('h2', {text: 'Unset'}),
                counts.unset,
                el('span', {className: 'group-hint muted', text: 'Add a username and password to enable a tracker'}),
            ]),
            groups.unset.list, groups.unset.empty,
        ])
        byId('tracker-groups').replaceChildren(enabledCard, disabledCard, unsetCard)
        return {groups: groups, counts: counts}
    }

    // Puts the rows in the list in order, only moving those not already in place, so a focused input keeps its focus
    function placeRows(list, rows) {
        rows.forEach(function (row, index) {
            const current = list.children[index]
            if (current !== row.element) {
                list.insertBefore(row.element, current || null)
            }
        })
        while (list.children.length > rows.length) {
            list.lastElementChild.remove()
        }
    }

    function renderTrackers() {
        if (!trackerGroups) {
            trackerGroups = buildTrackerGroups()
        }

        const search = byId('tracker-search').value.trim().toLowerCase()
        const rowsByGroup = {headless: [], manual: [], disabled: [], unset: []}
        trackers.forEach(function (tracker) {
            let row = trackerRowsByName.get(tracker.name)
            if (!row) {
                row = createTrackerRow(tracker)
                trackerRowsByName.set(tracker.name, row)
            }
            row.update(tracker)
            if (!search || tracker.name.toLowerCase().includes(search)) {
                rowsByGroup[displayedGroupOf(tracker)].push(row)
            }
        })

        const groups = trackerGroups.groups
        Object.keys(groups).forEach(function (key) {
            placeRows(groups[key].list, rowsByGroup[key])
            groups[key].empty.hidden = rowsByGroup[key].length > 0 || !groups[key].empty.textContent
        })
        groups.disabled.empty.textContent = search ? 'No disabled trackers match the search' : 'No disabled trackers'
        groups.disabled.empty.hidden = rowsByGroup.disabled.length > 0
        groups.unset.empty.textContent = search ? 'No unset trackers match the search' : 'Every tracker has credentials'
        groups.unset.empty.hidden = rowsByGroup.unset.length > 0
        groups.headless.title.textContent = `Headless (${rowsByGroup.headless.length})`
        groups.manual.title.textContent = `Manual (${rowsByGroup.manual.length})`
        trackerGroups.counts.enabled.textContent = `(${rowsByGroup.headless.length + rowsByGroup.manual.length})`
        trackerGroups.counts.disabled.textContent = `(${rowsByGroup.disabled.length})`
        trackerGroups.counts.unset.textContent = `(${rowsByGroup.unset.length})`

        const totalEnabled = trackers.filter(function (tracker) {
            return tracker.enabled
        }).length
        byId('tracker-summary').textContent = `${totalEnabled} of ${pluralise(trackers.length, 'supported tracker')} enabled`
        byId('adult-warning').hidden = adultTrackersEnabled() || !trackers.some(function (tracker) {
            return tracker.enabled && tracker.adult
        })
        renderSelection()
    }

    function formatLastRun(epochMillis) {
        const date = new Date(epochMillis)
        if (date.toDateString() === new Date().toDateString()) {
            return date.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})
        }
        return date.toLocaleDateString([], {day: 'numeric', month: 'short'})
    }

    function lastRunContent(tracker) {
        if (tracker.lastRunAt === null) {
            return {className: 'last-run tracker-extra muted', title: '', children: ['Never run']}
        }

        const succeeded = tracker.lastRunOk === true
        return {
            className: `last-run tracker-extra ${succeeded ? 'success' : 'failure'}`,
            title: `Last run ${succeeded ? 'succeeded' : 'failed'} at ${new Date(tracker.lastRunAt).toLocaleString()}`,
            children: [
                el('span', {'aria-hidden': 'true', text: succeeded ? '✓ ' : '✕ '}),
                el('span', {className: 'visually-hidden', text: succeeded ? 'Succeeded ' : 'Failed '}),
                formatLastRun(tracker.lastRunAt),
            ],
        }
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
        trackerRowsByName.forEach(function (row, name) {
            if (row.element.isConnected) {
                positions.set(name, row.element.getBoundingClientRect().top)
            }
        })
        return positions
    }

    // An element that stays where it is when a tracker moves, used to keep the page still: the row after the tracker, or the group after its
    // group (since its own group changes height), or the row before it
    function scrollAnchorFor(element) {
        const group = element.closest('.group')
        return element.nextElementSibling || group.nextElementSibling || element.previousElementSibling || group
    }

    // For several moved trackers, the first row on screen that is not moving
    function firstStillRowOnScreen(movedNames) {
        const headerBottom = document.querySelector('.app-header').getBoundingClientRect().bottom
        return Array.from(document.querySelectorAll('#tracker-groups .tracker-row')).find(function (element) {
            return !movedNames.includes(element.dataset.name) && element.getBoundingClientRect().top >= headerBottom
        }) || null
    }

    // Re-renders the trackers, with the moved trackers sliding from their old positions into their new groups (and the other rows sliding to
    // make room). The page does not scroll to follow a moved tracker: the rows around its old position stay where they are on screen
    function renderTrackersWithMove(moved) {
        const movedNames = Array.isArray(moved) ? moved : [moved]
        const movedRows = movedNames.map(function (name) {
            return trackerRowsByName.get(name)
        }).filter(Boolean)
        let anchor = null
        if (movedRows.length === 1 && movedRows[0].element.isConnected) {
            anchor = scrollAnchorFor(movedRows[0].element)
        } else if (movedRows.length > 1) {
            anchor = firstStillRowOnScreen(movedNames)
        }
        const anchorTop = anchor ? anchor.getBoundingClientRect().top : 0
        const before = trackerRowPositions()
        renderTrackers()

        if (anchor && anchor.isConnected) {
            const shift = anchor.getBoundingClientRect().top - anchorTop
            if (shift !== 0) {
                window.scrollBy({top: shift, behavior: 'instant'})
            }
        }

        movedRows.forEach(function (row) {
            if (row.element.isConnected) {
                row.element.classList.remove('just-moved')
                // Restarts the highlight animation, in case the tracker moved again before it finished
                row.element.getBoundingClientRect()
                row.element.classList.add('just-moved')
            }
        })
        if (REDUCED_MOTION_QUERY.matches) {
            return
        }

        trackerRowPositions().forEach(function (top, name) {
            const previous = before.get(name)
            const offset = previous === undefined ? 0 : previous - top
            if (offset === 0) {
                return
            }

            trackerRowsByName.get(name).element.animate(
                [{transform: `translateY(${offset}px)`}, {transform: 'translateY(0)'}],
                {duration: movedNames.includes(name) ? 700 : 350, easing: 'cubic-bezier(0.2, 0.8, 0.2, 1)'}
            )
        })
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Trackers: selecting several trackers (Ctrl/Cmd+click, Ctrl/Cmd+Shift+click for a range) to change them together
    // ---------------------------------------------------------------------------------------------------------------

    const selectedTrackers = new Set()
    let selectionAnchor = null

    // Clicks on the inputs, switch and buttons of a row keep their usual behaviour
    function isRowControl(target) {
        return Boolean(target.closest('input, button, label.switch'))
    }

    function onTrackerRowClick(event, name) {
        if (!(event.ctrlKey || event.metaKey) || isRowControl(event.target)) {
            return
        }
        event.preventDefault()

        if (event.shiftKey && selectionAnchor) {
            // The trackers shown between the last clicked tracker and this one, in the order they are listed
            const names = Array.from(document.querySelectorAll('#tracker-groups .tracker-row')).map(function (element) {
                return element.dataset.name
            })
            const from = names.indexOf(selectionAnchor)
            const to = names.indexOf(name)
            if (from !== -1 && to !== -1) {
                names.slice(Math.min(from, to), Math.max(from, to) + 1).forEach(function (rangeName) {
                    selectedTrackers.add(rangeName)
                })
                renderSelection()
                return
            }
        }

        if (selectedTrackers.has(name)) {
            selectedTrackers.delete(name)
        } else {
            selectedTrackers.add(name)
        }
        selectionAnchor = name
        renderSelection()
    }

    function clearSelection() {
        selectedTrackers.clear()
        selectionAnchor = null
        renderSelection()
    }

    function selectedTrackerList() {
        return trackers.filter(function (tracker) {
            return selectedTrackers.has(tracker.name)
        })
    }

    function renderSelection() {
        trackerRowsByName.forEach(function (row, name) {
            row.element.classList.toggle('selected', selectedTrackers.has(name))
        })

        const selected = selectedTrackerList()
        const withCredentials = selected.filter(hasCredentials)
        byId('selection-bar').hidden = selected.length === 0
        byId('selection-count').textContent = `${pluralise(selected.length, 'tracker')} selected`
        byId('selection-enable').disabled = !withCredentials.some(function (tracker) {
            return !tracker.enabled
        })
        byId('selection-disable').disabled = !selected.some(function (tracker) {
            return tracker.enabled
        })
        byId('selection-remove').disabled = !selected.some(function (tracker) {
            return tracker.username || tracker.hasPassword
        })
    }

    // Applies a change to each selected tracker in turn, then moves them all together
    function changeSelectedTrackers(action) {
        const selected = selectedTrackerList()
        let targets
        let send
        let describe
        if (action === 'remove') {
            targets = selected.filter(function (tracker) {
                return tracker.username || tracker.hasPassword
            })
            if (!window.confirm(`Remove the saved credentials for ${pluralise(targets.length, 'tracker')}?`)) {
                return
            }
            send = function (tracker) {
                return request('DELETE', `/api/trackers/${encodeURIComponent(tracker.name)}`)
            }
            describe = 'Removed the credentials for'
        } else {
            const enable = action === 'enable'
            targets = selected.filter(function (tracker) {
                return tracker.enabled !== enable && (!enable || hasCredentials(tracker))
            })
            send = function (tracker) {
                return request('PUT', '/api/trackers', {name: tracker.name, username: tracker.username, password: null, enabled: enable})
            }
            describe = enable ? 'Enabled' : 'Disabled'
        }

        const skipped = selected.length - targets.length
        const changed = []
        ;['selection-enable', 'selection-disable', 'selection-remove'].forEach(function (id) {
            byId(id).disabled = true
        })
        targets.reduce(function (previous, tracker) {
            return previous.then(function () {
                return send(tracker).then(function (updated) {
                    trackers = updated
                    changed.push(tracker.name)
                })
            })
        }, Promise.resolve())
            .then(function () {
                const skippedMessage = skipped > 0 ? ` (${skipped} skipped)` : ''
                showToast(`${describe} ${pluralise(changed.length, 'tracker')}${skippedMessage}`)
            })
            .catch(function (error) {
                showToast(`${describe} ${pluralise(changed.length, 'tracker')}, then failed: ${error.message}`, 'error')
            })
            .finally(function () {
                changed.forEach(function (name) {
                    unpinTracker(name)
                    trackerRowsByName.get(name).resetInput()
                })
                clearSelection()
                renderTrackersWithMove(changed)
            })
    }

    function createTrackerRow(tracker) {
        let current = tracker
        // The values last saved from this row, used to decide whether there is anything new to save
        const saved = {username: tracker.username, password: tracker.hasPassword ? SAVED_PASSWORD_VALUE : ''}
        let typingTimer = null
        let saveQueue = Promise.resolve()

        const row = el('div', {className: 'tracker-row', dataset: {name: tracker.name}})
        const errorMessage = el('p', {className: 'tracker-error', hidden: true})

        const usernameInput = el('input', {
            type: 'text', placeholder: 'Username or email', autocomplete: 'off', 'aria-label': `${tracker.name} username`,
        })
        usernameInput.value = tracker.username
        const passwordInput = el('input', {
            type: 'password', placeholder: 'Password', autocomplete: 'new-password', 'aria-label': `${tracker.name} password`,
        })
        passwordInput.value = saved.password
        const enabledInput = el('input', {type: 'checkbox', role: 'switch', 'aria-label': `Enable ${tracker.name}`})
        const removeButton = el('button', {className: 'button danger small', type: 'button', text: 'Remove', title: 'Remove the saved credentials'})
        const lastRun = el('span')

        function hasUnsavedInput() {
            return usernameInput.value !== saved.username || passwordInput.value !== saved.password
        }

        function refreshRow() {
            const credentialsSet = hasCredentials(current)
            row.classList.toggle('unset', !credentialsSet)
            enabledInput.disabled = !credentialsSet
            removeButton.disabled = !credentialsSet
            row.classList.toggle('editing', hasUnsavedInput())
            if (hasUnsavedInput()) {
                trackerDrafts[current.name] = true
            } else {
                delete trackerDrafts[current.name]
            }
        }

        // Called with the latest values from the server; input being typed is kept
        function update(updated) {
            current = updated
            if (!hasUnsavedInput() && !row.contains(document.activeElement)) {
                usernameInput.value = updated.username
                passwordInput.value = updated.hasPassword ? SAVED_PASSWORD_VALUE : ''
                saved.username = usernameInput.value
                saved.password = passwordInput.value
            }
            enabledInput.checked = updated.enabled
            const content = lastRunContent(updated)
            lastRun.className = content.className
            lastRun.title = content.title
            lastRun.replaceChildren.apply(lastRun, content.children)
            refreshRow()
        }

        function savedMessage(values, enabled) {
            if (enabled !== current.enabled) {
                return `${enabled ? 'Enabled' : 'Disabled'} ${current.name}`
            }
            const changed = []
            if (values.username !== saved.username) {
                changed.push('username')
            }
            if (values.password !== saved.password && !values.password) {
                return `Removed the password for ${current.name}`
            }
            if (values.password !== saved.password) {
                changed.push('password')
            }
            return `Saved ${changed.join(' and ') || 'credentials'} for ${current.name}`
        }

        function save(requestedEnabled) {
            const values = {username: usernameInput.value, password: passwordInput.value}
            // The saved password is kept unless it was changed, and a tracker whose username or password is removed is disabled
            const password = values.password === SAVED_PASSWORD_VALUE ? null : values.password
            const enabled = requestedEnabled && values.username.trim() !== '' && password !== ''
            errorMessage.hidden = true
            return request('PUT', '/api/trackers', {name: current.name, username: values.username, password: password, enabled: enabled})
                .then(function (updated) {
                    const message = savedMessage(values, requestedEnabled)
                    // Only a change to the switch moves the tracker straight away; removing its credentials waits like any other edit
                    const toggled = requestedEnabled !== current.enabled
                    const previousGroup = displayedGroupOf(current)
                    trackers = updated
                    current = findTracker(current.name)
                    saved.username = values.username
                    saved.password = values.password
                    showToast(message)

                    if (toggled) {
                        unpinTracker(current.name)
                        renderTrackersWithMove(current.name)
                    } else if (trackerGroupOf(current) !== previousGroup && row.contains(document.activeElement)) {
                        // Still being edited, so stays where it is until the user clicks elsewhere, or stops changing it
                        pinTracker(current.name, previousGroup)
                        renderTrackers()
                    } else if (trackerGroupOf(current) !== previousGroup) {
                        unpinTracker(current.name)
                        renderTrackersWithMove(current.name)
                    } else {
                        renderTrackers()
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
            touchPinnedTracker(current.name)
            clearTimeout(typingTimer)
            typingTimer = setTimeout(saveCredentialsIfChanged, TYPING_SAVE_DELAY_MS)
        }

        // The saved password is selected when the field is focused, so typing replaces it and deleting removes it
        passwordInput.addEventListener('focus', function () {
            if (passwordInput.value === SAVED_PASSWORD_VALUE) {
                passwordInput.select()
            }
        })

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
            request('DELETE', `/api/trackers/${encodeURIComponent(current.name)}`)
                .then(function (updated) {
                    trackers = updated
                    current = findTracker(current.name)
                    resetInput()
                    unpinTracker(current.name)
                    showToast(`Removed the credentials for ${current.name}`)
                    renderTrackersWithMove(current.name)
                })
                .catch(function (error) {
                    showToast(error.message, 'error')
                })
        })

        row.addEventListener('click', function (event) {
            onTrackerRowClick(event, current.name)
        })
        // Stops Ctrl/Shift+click from selecting the text of the rows
        row.addEventListener('mousedown', function (event) {
            if ((event.ctrlKey || event.metaKey) && !isRowControl(event.target)) {
                event.preventDefault()
            }
        })

        // Called when the tracker is changed from outside the row (such as removed with other selected trackers)
        function resetInput() {
            usernameInput.value = ''
            passwordInput.value = ''
            saved.username = ''
            saved.password = ''
        }

        const url = tracker.urls[0]
        const nameCell = el('div', {className: 'tracker-name'}, [
            el('a', {className: 'tracker-link', href: url, target: '_blank', rel: 'noopener noreferrer', title: `Open ${tracker.name} (${url})`}, [
                tracker.name,
                el('span', {className: 'external-icon', 'aria-hidden': 'true', text: '↗'}),
            ]),
            tracker.type === 'MANUAL' ? el('span', {className: 'badge manual', text: 'Manual', title: 'Requires user input (e.g. captcha)'}) : null,
            tracker.adult ? el('span', {className: 'badge adult', text: 'Adult'}) : null,
        ])
        const enabledCell = el('label', {className: 'switch tracker-extra', title: 'Enabled'}, [enabledInput, el('span', {className: 'switch-track'})])

        row.append(nameCell, usernameInput, passwordInput, lastRun, enabledCell, el('div', {className: 'tracker-actions tracker-extra'}, [removeButton]),
            errorMessage)
        update(tracker)
        return {
            element: row,
            update: update,
            resetInput: function () {
                resetInput()
                update(current)
            },
        }
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
            applyRunStateToSettings(latestStatus)
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

    // Settings cannot be changed while an execution is in progress, so every setting is locked until it ends
    function applyRunStateToSettings(status) {
        const active = isActive(status)
        Object.keys(settingRows).forEach(function (name) {
            settingRows[name].setLocked(active)
        })
        byId('run-options-locked').hidden = !active
        byId('settings-locked').hidden = !active
    }

    // A list of the allowed values in order, where each can be dragged (or moved with the arrow keys) into position, and included or excluded
    function sortableChoiceInput(setting, onChange, label) {
        const list = el('ol', {className: 'sortable', 'aria-label': label})
        let order = setting.allowedValues.slice()
        let selected = new Set()
        let disabled = !setting.editable
        const items = new Map()

        function value() {
            return order.filter(function (item) {
                return selected.has(item)
            }).join(',')
        }

        function render() {
            const focused = document.activeElement
            order.forEach(function (item, index) {
                const entry = items.get(item)
                if (list.children[index] !== entry.element) {
                    list.insertBefore(entry.element, list.children[index] || null)
                }
                entry.position.textContent = String(index + 1)
                entry.element.classList.toggle('excluded', !selected.has(item))
                entry.checkbox.checked = selected.has(item)
                entry.checkbox.disabled = disabled
                entry.handle.disabled = disabled
            })
            list.classList.toggle('disabled', disabled)
            // Moving an item removes the keyboard focus from its handle
            if (focused && list.contains(focused) && document.activeElement !== focused) {
                focused.focus()
            }
        }

        function changed() {
            render()
            onChange(value(), true)
        }

        function move(item, offset) {
            const index = order.indexOf(item)
            const target = index + offset
            if (target < 0 || target >= order.length) {
                return
            }
            order = order.slice()
            order.splice(index, 1)
            order.splice(target, 0, item)
            changed()
        }

        // Dragging moves the item in the list as the pointer passes the middle of its neighbours, and saves the order when it is dropped
        function startDrag(item, event) {
            if (disabled || event.button !== 0) {
                return
            }
            event.preventDefault()
            const entry = items.get(item)
            const startOrder = order.join(',')
            entry.element.classList.add('dragging')

            function onMove(moveEvent) {
                const index = order.indexOf(item)
                const previous = list.children[index - 1]
                const next = list.children[index + 1]
                if (previous && moveEvent.clientY < previous.getBoundingClientRect().top + previous.offsetHeight / 2) {
                    order.splice(index, 1)
                    order.splice(index - 1, 0, item)
                    render()
                } else if (next && moveEvent.clientY > next.getBoundingClientRect().top + next.offsetHeight / 2) {
                    order.splice(index, 1)
                    order.splice(index + 1, 0, item)
                    render()
                }
            }

            // Listened to on the document (not captured by the handle), since moving the item in the list releases a pointer capture
            function onEnd() {
                document.removeEventListener('pointermove', onMove)
                document.removeEventListener('pointerup', onEnd)
                document.removeEventListener('pointercancel', onEnd)
                entry.element.classList.remove('dragging')
                if (order.join(',') !== startOrder) {
                    changed()
                }
            }

            document.addEventListener('pointermove', onMove)
            document.addEventListener('pointerup', onEnd)
            document.addEventListener('pointercancel', onEnd)
        }

        setting.allowedValues.forEach(function (item) {
            const name = formatTypeName(item)
            const handle = el('button', {
                type: 'button', className: 'drag-handle', 'aria-label': `Move ${name} (use the up and down arrow keys)`,
                title: 'Drag to change the order', text: '⠿',
            })
            const checkbox = el('input', {type: 'checkbox', role: 'switch', 'aria-label': `Include ${name}`})
            const position = el('span', {className: 'sortable-position', 'aria-hidden': 'true'})
            const element = el('li', {className: 'sortable-item', dataset: {item: item}}, [
                handle,
                position,
                el('span', {className: 'sortable-name', text: name}),
                el('label', {className: 'switch', title: `Include ${name}`}, [checkbox, el('span', {className: 'switch-track'})]),
            ])

            handle.addEventListener('pointerdown', function (event) {
                startDrag(item, event)
            })
            handle.addEventListener('keydown', function (event) {
                if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
                    event.preventDefault()
                    move(item, event.key === 'ArrowUp' ? -1 : 1)
                }
            })
            checkbox.addEventListener('change', function () {
                if (!checkbox.checked && selected.size === 1) {
                    checkbox.checked = true
                    showToast(`${setting.label}: at least one must be included`, 'error')
                    return
                }
                if (checkbox.checked) {
                    selected.add(item)
                } else {
                    selected.delete(item)
                }
                changed()
            })
            items.set(item, {element: element, handle: handle, checkbox: checkbox, position: position})
        })

        return {
            element: list,
            setValue: function (newValue) {
                const values = newValue.split(',').map(function (item) {
                    return item.trim().toUpperCase()
                }).filter(function (item) {
                    return items.has(item)
                })
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
        }
    }

    // A timeout that is either disabled (0), or a number of seconds
    function timeoutInput(setting, onChange, onFlush) {
        const label = setting.label
        const checkbox = el('input', {type: 'checkbox', role: 'switch', 'aria-label': `${label} enabled`})
        const seconds = el('input', {type: 'number', min: '1', 'aria-label': `${label} in seconds`})
        const secondsField = el('span', {className: 'timeout-seconds'}, [seconds, el('span', {className: 'muted', text: 'seconds'})])
        let lastEnabledValue = String(DEFAULT_INPUT_TIMEOUT_SECONDS)
        let disabled = !setting.editable

        function refresh() {
            secondsField.hidden = !checkbox.checked
            checkbox.disabled = disabled
            seconds.disabled = disabled
        }

        checkbox.addEventListener('change', function () {
            if (checkbox.checked) {
                seconds.value = lastEnabledValue
                refresh()
                onChange(lastEnabledValue, true)
                seconds.focus()
            } else {
                refresh()
                onChange('0', true)
            }
        })
        seconds.addEventListener('input', function () {
            if (Number(seconds.value) > 0) {
                lastEnabledValue = seconds.value
            }
            onChange(seconds.value, false)
        })
        seconds.addEventListener('blur', onFlush)
        seconds.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                onFlush()
            }
        })

        return {
            element: el('span', {className: 'timeout-input'}, [
                secondsField,
                el('label', {className: 'switch'}, [checkbox, el('span', {className: 'switch-track'})]),
            ]),
            setValue: function (value) {
                const enabled = Number(value) > 0
                checkbox.checked = enabled
                if (enabled) {
                    seconds.value = value
                    lastEnabledValue = value
                }
                refresh()
            },
            setDisabled: function (isDisabled) {
                disabled = isDisabled || !setting.editable
                refresh()
            },
        }
    }

    // Creates the input for a setting. 'onChange' is called with the new value and whether to save it immediately (rather than after typing)
    function settingInput(setting, onChange, onFlush) {
        const disabled = !setting.editable
        const label = setting.label

        if (setting.name === INPUT_TIMEOUT_SETTING) {
            return timeoutInput(setting, onChange, onFlush)
        }

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
            return sortableChoiceInput(setting, onChange, label)
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

    function showRowError(row, errorMessage, message) {
        row.classList.toggle('invalid', Boolean(message))
        errorMessage.textContent = message || ''
        errorMessage.hidden = !message
    }

    // A run option on the Main tab
    function renderOptionRow(setting) {
        const name = setting.name
        const row = el('div', {className: `option-row${setting.type === 'MULTI_SELECT' ? ' wide' : ''}`, title: name})
        const errorMessage = el('p', {className: 'setting-error', hidden: true})
        const control = createControl(setting)

        function refresh() {
            const current = findSetting(name)
            row.classList.toggle('changed', current.changedFromDefault)
            row.classList.toggle('editing', hasKey(unsavedSettings, name))
        }

        settingRows[name] = {
            refresh: refresh,
            setValue: control.setValue,
            showError: function (message) {
                showRowError(row, errorMessage, message)
            },
            setLocked: function (locked) {
                row.classList.toggle('locked', locked)
                control.setDisabled(locked)
            },
        }
        refresh()

        row.append(
            el('div', {className: 'option-label'}, [el('span', {text: setting.label})]),
            el('div', {className: 'option-control'}, [control.element]),
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
        const errorMessage = el('p', {className: 'setting-error', hidden: true})
        const control = createControl(setting)

        // Updates the badges, without replacing the input (which may have focus)
        function refresh() {
            const current = findSetting(name)
            row.classList.toggle('changed', current.changedFromDefault)
            row.classList.toggle('editing', hasKey(unsavedSettings, name))

            const badges = [el('strong', {className: 'setting-label', text: current.label}), el('code', {text: name})]
            if (current.source === 'WEB_UI') {
                badges.push(el('span', {className: 'badge source-web_ui', text: 'Saved'}))
            }
            if (!current.editable) {
                badges.push(el('span', {className: 'badge', text: 'Environment only', title: 'Read at startup; set as an environment variable'}))
            }
            nameLine.replaceChildren.apply(nameLine, badges)

            const overridesEnvironment = current.source === 'WEB_UI' && current.environmentValue !== null
            meta.hidden = !overridesEnvironment
            meta.textContent = overridesEnvironment ? `Environment: ${current.environmentValue}` : ''
        }

        settingRows[name] = {
            refresh: refresh,
            setValue: control.setValue,
            showError: function (message) {
                showRowError(row, errorMessage, message)
            },
            setLocked: function (locked) {
                row.classList.toggle('locked', locked)
                control.setDisabled(locked)
            },
        }
        refresh()

        row.append(
            el('div', {}, [nameLine, el('p', {className: 'setting-description', text: setting.description}), meta]),
            el('div', {className: 'setting-input'}, [control.element, errorMessage])
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
        return `Saved ${labels.join(', ')}`
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
            })
            .catch(function (error) {
                const setting = findSetting(name)
                settingRows[name].showError(error.message)
                showToast(`${setting ? setting.label : name} was not saved: ${error.message}`, 'error')
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
        setUpGallery()
        const executionLog = byId('execution-log')
        executionLog.addEventListener('toggle', function () {
            if (executionLog.open) {
                fetchLogs(executionLog.querySelector('.log-output'))
            }
        })
        byId('tracker-search').addEventListener('input', renderTrackers)
        byId('selection-enable').addEventListener('click', function () {
            changeSelectedTrackers('enable')
        })
        byId('selection-disable').addEventListener('click', function () {
            changeSelectedTrackers('disable')
        })
        byId('selection-remove').addEventListener('click', function () {
            changeSelectedTrackers('remove')
        })
        byId('selection-clear').addEventListener('click', clearSelection)
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && selectedTrackers.size > 0) {
                clearSelection()
            }
        })
        document.addEventListener('pointerdown', function (event) {
            releasePinnedTrackersOutside(event.target)
        }, true)
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

import {ADULT_TRACKERS_SETTING, REDUCED_MOTION_QUERY, SAVED_PASSWORD_VALUE, TYPING_SAVE_DELAY_MS, UNSET_MOVE_DELAY_MS} from './constants.mjs'
import {latestStatus, runDescription} from './run.mjs'
import {updateTrackerScreenshots} from './screenshots.mjs'
import {findSetting} from './settings.mjs'
import {byId, el, pluralise, request, showToast} from './util.mjs'

// ---------------------------------------------------------------------------------------------------------------
// Trackers
// ---------------------------------------------------------------------------------------------------------------

export let trackers = []
export let trackersLoaded = false
// Input not yet saved, per tracker, so a warning is shown before leaving the page
export const trackerDrafts = {}
// Rows are kept between renders (and moved between groups), so a focused input is never replaced, keyed by tracker name
const trackerRowsByName = new Map()
// Trackers kept in the group they were shown in after their credentials changed, until the user clicks elsewhere or stops changing them
const pinnedTrackers = new Map()
let trackerGroups = null

function adultTrackersEnabled() {
    const setting = findSetting(ADULT_TRACKERS_SETTING)
    return !setting || setting.value.toLowerCase() === 'true'
}

export function loadTrackers() {
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
export function releasePinnedTrackersOutside(target) {
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

export function renderTrackers() {
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
    const options = {day: 'numeric', month: 'short'}
    if (date.getFullYear() !== new Date().getFullYear()) {
        options.year = 'numeric'
    }
    return date.toLocaleDateString([], options)
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

// Expands the row to show the latest screenshots of the tracker (from any execution, kept across restarts), as on the Main tab: the profile
// screenshots of its latest successful run, and the failure screenshot of its latest run if it failed
function updateLatestScreenshotsButton(button, tracker, expanded) {
    const count = tracker.screenshots.length
    const failure = tracker.screenshots.some(function (screenshot) {
        return screenshot.failure
    })
    button.hidden = count === 0
    button.classList.toggle('failure', failure)
    button.querySelector('.latest-screenshots-count').textContent = count > 1 ? String(count) : ''
    const description = count > 1 ? `the ${count} latest screenshots` : 'the latest screenshot'
    const action = expanded ? 'Hide' : 'Show'
    const failureNote = failure ? ', including the failure screenshot of the latest run' : ''
    button.title = count === 0 ? '' : `${action} ${description}${failureNote}`
    button.setAttribute('aria-label', `${action} ${description} of ${tracker.name}${failureNote}`)
    button.setAttribute('aria-expanded', String(expanded))
}

function imageIcon() {
    const namespace = 'http://www.w3.org/2000/svg'
    const svg = document.createElementNS(namespace, 'svg')
    svg.setAttribute('viewBox', '0 0 24 24')
    svg.setAttribute('aria-hidden', 'true')
    const shapes = [
        ['rect', {x: '3', y: '4', width: '18', height: '16', rx: '2'}],
        ['circle', {cx: '9', cy: '10', r: '2'}],
        ['path', {d: 'M21 16l-5-5-9 9'}],
    ]
    shapes.forEach(function (shape) {
        const child = document.createElementNS(namespace, shape[0])
        Object.keys(shape[1]).forEach(function (attribute) {
            child.setAttribute(attribute, shape[1][attribute])
        })
        svg.append(child)
    })
    return svg
}

export function importTrackers(file) {
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

export const selectedTrackers = new Set()
let selectionAnchor = null

// Clicks on the inputs, switch, buttons, links and screenshots of a row keep their usual behaviour
function isRowControl(target) {
    return Boolean(target.closest('input, button, a, label.switch, .tracker-screenshots'))
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

export function clearSelection() {
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

// Applies a change to every selected tracker in a single request, then moves them all together
export function changeSelectedTrackers(action) {
    const selected = selectedTrackerList()
    let targets
    let describe
    if (action === 'remove') {
        targets = selected.filter(function (tracker) {
            return tracker.username || tracker.hasPassword
        })
        if (!window.confirm(`Remove the saved credentials for ${pluralise(targets.length, 'tracker')}?`)) {
            return
        }
        describe = 'Removed the credentials for'
    } else {
        const enable = action === 'enable'
        targets = selected.filter(function (tracker) {
            return tracker.enabled !== enable && (!enable || hasCredentials(tracker))
        })
        describe = enable ? 'Enabled' : 'Disabled'
    }

    let changed = []
    ;['selection-enable', 'selection-disable', 'selection-remove'].forEach(function (id) {
        byId(id).disabled = true
    })
    request('POST', '/api/trackers/bulk', {
        action: action,
        names: targets.map(function (tracker) {
            return tracker.name
        }),
    })
        .then(function (result) {
            trackers = result.trackers
            changed = result.changed
            const skipped = selected.length - changed.length
            const skippedMessage = skipped > 0 ? ` (${skipped} skipped)` : ''
            showToast(`${describe} ${pluralise(changed.length, 'tracker')}${skippedMessage}`)
        })
        .catch(function (error) {
            showToast(`Unable to change the selected trackers: ${error.message}`, 'error')
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
    // The latest screenshots, shown below the row when expanded
    const screenshotsPanel = el('div', {className: 'tracker-screenshots', hidden: true})
    let screenshotsExpanded = false
    const screenshotsButton = el('button', {
        className: 'icon-button small latest-screenshots',
        type: 'button',
        'aria-controls': `latest-screenshots-${tracker.name}`,
        onclick: function () {
            setScreenshotsExpanded(!screenshotsExpanded)
        },
    }, [imageIcon(), el('span', {className: 'latest-screenshots-count'})])
    screenshotsPanel.id = `latest-screenshots-${tracker.name}`

    // Collapsing removes the thumbnails, so they are only fetched while the row is expanded
    function setScreenshotsExpanded(expanded) {
        screenshotsExpanded = expanded && current.screenshots.length > 0
        row.classList.toggle('expanded', screenshotsExpanded)
        if (screenshotsExpanded) {
            updateTrackerScreenshots(screenshotsPanel, current, true)
        } else {
            screenshotsPanel.hidden = true
            delete screenshotsPanel.dataset.signature
            screenshotsPanel.replaceChildren()
        }
        updateLatestScreenshotsButton(screenshotsButton, current, screenshotsExpanded)
    }

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
        setScreenshotsExpanded(screenshotsExpanded && hasCredentials(updated))
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

    const screenshotsCell = el('div', {className: 'tracker-screenshots-cell tracker-extra'}, [screenshotsButton])
    row.append(nameCell, usernameInput, passwordInput, lastRun, screenshotsCell, enabledCell, el('div', {className: 'tracker-actions tracker-extra'}, [removeButton]),
        errorMessage, screenshotsPanel)
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

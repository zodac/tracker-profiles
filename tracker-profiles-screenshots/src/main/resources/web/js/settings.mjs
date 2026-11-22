import {NUMBER_LINE_ALL_LABELS, NUMBER_LINE_MAXIMUM_VALUES, TYPING_SAVE_DELAY_MS} from './constants.mjs'
import {formatTypeName, isActive, latestStatus, refreshStatus} from './run.mjs'
import {loadTrackers, renderTrackers, trackersLoaded} from './trackers.mjs'
import {byId, confirmThenRun, el, hasKey, pluralise, request, showToast} from './util.mjs'

// ---------------------------------------------------------------------------------------------------------------
// Settings (on the Settings tab)
// ---------------------------------------------------------------------------------------------------------------

let settings = []
// Values changed in the UI but not yet saved (including any rejected by the server), keyed by setting name
export const unsavedSettings = {}
const settingTimers = {}
const settingRows = {}
let settingsSaveQueue = Promise.resolve()

export function currentSettingValue(name) {
    const setting = findSetting(name)
    return setting ? setting.value : null
}

export function findSetting(name) {
    return settings.find(function (setting) {
        return setting.name === name
    })
}

function hasPendingSettingChanges() {
    return Object.keys(unsavedSettings).length > 0 || Object.keys(settingTimers).length > 0
}

export function loadSettings() {
    // Reloading replaces the inputs, so is skipped while a change is waiting to be saved
    if (hasPendingSettingChanges()) {
        return Promise.resolve()
    }

    return request('GET', '/api/settings').then(function (loaded) {
        settings = loaded
        renderSettings()
        applyRunStateToSettings(latestStatus)
        if (trackersLoaded) {
            renderTrackers()
        }
    })
}

function renderSettings() {
    const groups = []
    // Settings are listed in the order of their groups
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

// Settings cannot be changed while an execution is in progress, so every setting is locked until it ends
export function applyRunStateToSettings(status) {
    const active = isActive(status)
    Object.keys(settingRows).forEach(function (name) {
        settingRows[name].setLocked(active)
    })
    byId('settings-locked').hidden = !active
    byId('backup-restore').disabled = active
    byId('backup-restore-row').classList.toggle('locked', active)
}

// ---------------------------------------------------------------------------------------------------------------
// Backup (on the Settings tab): the Download backup menu chooses whether the credentials and screenshots are included. The first click of Restore
// only asks for confirmation (see confirmThenRun), and the second chooses the file
// ---------------------------------------------------------------------------------------------------------------

export function updateBackupDownload() {
    const credentials = byId('backup-credentials').checked
    const screenshots = byId('backup-screenshots').checked
    byId('backup-download').href = `/api/backup?credentials=${credentials}&screenshots=${screenshots}`
}

// The number of screenshots included is shown when the menu is opened, from the latest status
export function onBackupMenuToggled() {
    if (!byId('backup-menu').open) {
        return
    }
    const all = latestStatus && latestStatus.downloads ? latestStatus.downloads.find(function (download) {
        return download.type === 'all'
    }) : null
    byId('backup-screenshot-count').textContent = all ? String(all.count) : ''
}

export function closeBackupMenu() {
    byId('backup-menu').open = false
}

export function chooseBackupToRestore() {
    confirmThenRun('restore-backup', function () {
        byId('backup-file').click()
    })
}

export function restoreBackup(file) {
    const restoreButton = byId('backup-restore')
    restoreButton.disabled = true
    fetch('/api/backup', {method: 'POST', headers: {'Content-Type': 'application/zip'}, body: file})
        .then(async function (response) {
            const text = await response.text()
            const json = text ? JSON.parse(text) : null
            if (!response.ok) {
                throw new Error(json && json.error ? json.error : `Request failed with status ${response.status}`)
            }
            return json
        })
        .then(function (result) {
            // The restored settings replace any change waiting to be saved
            Object.keys(settingTimers).forEach(function (name) {
                clearTimeout(settingTimers[name])
                delete settingTimers[name]
            })
            Object.keys(unsavedSettings).forEach(function (name) {
                delete unsavedSettings[name]
            })

            showToast(restoreMessage(file.name, result))
            return Promise.all([loadSettings(), loadTrackers(), refreshStatus()])
        })
        .catch(function (error) {
            showToast(`${file.name} was not restored: ${error.message}`, 'error')
        })
        .finally(function () {
            restoreButton.disabled = isActive(latestStatus)
            byId('backup-file').value = ''
        })
}

function restoreMessage(fileName, result) {
    const restored = [pluralise(result.settings, 'setting')]
    if (result.credentialsIncluded) {
        restored.push(pluralise(result.trackers, 'tracker'))
    }
    if (result.screenshotsIncluded) {
        restored.push(pluralise(result.screenshots, 'screenshot'))
    }

    let message = `Restored ${fileName}: ${restored.join(', ')}`
    if (result.unsupportedTrackers.length > 0) {
        message += `. Not restored, since they are not supported: ${result.unsupportedTrackers.join(', ')}`
    }
    if (result.ignoredSettings.length > 0) {
        message += `. Not restored, since they no longer exist or can only be set as environment variables: ${result.ignoredSettings.join(', ')}`
    }
    return message
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

// The step between the labelled values of a number line, so that a long range stays readable (every value still has a tick)
function numberLineLabelStep(count) {
    if (count <= NUMBER_LINE_ALL_LABELS) {
        return 1
    }
    return Math.ceil(count / NUMBER_LINE_ALL_LABELS / 2) * 2
}

// An integer setting with a small range, as a number line of every valid value: a slider with a tick for each value, which saves when released
function numberLineInput(setting, onChange, label) {
    const minimum = setting.minimum
    const maximum = setting.maximum
    const labelStep = numberLineLabelStep(maximum - minimum + 1)
    let disabled = !setting.editable

    const slider = el('input', {
        type: 'range',
        min: String(minimum),
        max: String(maximum),
        step: '1',
        disabled: disabled,
        'aria-label': label,
    })
    const current = el('output', {className: 'number-line-value', 'aria-hidden': 'true'})
    const ticks = el('div', {className: 'number-line-ticks'})

    function position(value) {
        return maximum === minimum ? 0 : (value - minimum) / (maximum - minimum)
    }

    function render() {
        const value = Number(slider.value)
        slider.style.setProperty('--fill', String(position(value)))
        current.textContent = slider.value
        ticks.querySelectorAll('.number-line-tick').forEach(function (tick) {
            tick.classList.toggle('selected', Number(tick.dataset.value) === value)
            tick.classList.toggle('filled', Number(tick.dataset.value) <= value)
        })
    }

    function select(value) {
        if (disabled || String(value) === slider.value) {
            return
        }
        slider.value = String(value)
        render()
        onChange(slider.value, true)
    }

    for (let value = minimum; value <= maximum; value++) {
        const labelled = value === minimum || value === maximum || value % labelStep === 0
        const tickValue = value
        ticks.append(el('span', {
            className: labelled ? 'number-line-tick labelled' : 'number-line-tick',
            dataset: {value: String(value)},
            onclick: function () {
                select(tickValue)
            },
        }, [labelled ? el('span', {className: 'number-line-label', text: String(value)}) : null]))
    }
    ticks.querySelectorAll('.number-line-tick').forEach(function (tick) {
        tick.style.setProperty('--position', String(position(Number(tick.dataset.value))))
    })

    slider.addEventListener('input', render)
    slider.addEventListener('change', function () {
        onChange(slider.value, true)
    })

    const element = el('div', {className: 'number-line'}, [el('div', {className: 'number-line-track'}, [slider, ticks]), current])
    return {
        element: element,
        setValue: function (value) {
            const number = Number.parseInt(value, 10)
            slider.value = String(Number.isNaN(number) ? minimum : Math.min(maximum, Math.max(minimum, number)))
            render()
        },
        setDisabled: function (isDisabled) {
            disabled = isDisabled || !setting.editable
            slider.disabled = disabled
            element.classList.toggle('disabled', disabled)
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
            return el('option', {value: value, text: formatTypeName(value)})
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

    if (setting.type === 'INTEGER' && setting.minimum !== null && setting.maximum !== null
        && setting.maximum - setting.minimum < NUMBER_LINE_MAXIMUM_VALUES) {
        return numberLineInput(setting, onChange, label)
    }

    const input = el('input', {
        type: setting.type === 'INTEGER' ? 'number' : 'text',
        min: setting.minimum === null ? null : String(setting.minimum),
        max: setting.maximum === null ? null : String(setting.maximum),
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

// A setting on the Settings tab
function renderSettingRow(setting) {
    const name = setting.name
    const row = el('div', {className: 'setting-row'})
    const nameLine = el('div', {className: 'setting-name'})
    const errorMessage = el('p', {className: 'setting-error', hidden: true})
    const control = createControl(setting)

    // Updates the badges, without replacing the input (which may have focus)
    function refresh() {
        const current = findSetting(name)
        // Marked only when a value saved in the UI changes the value of the deployment (its environment variable, or the default if not set)
        row.classList.toggle('changed', current.changedInWebUi)
        row.classList.toggle('editing', hasKey(unsavedSettings, name))

        nameLine.replaceChildren(el('strong', {className: 'setting-label', text: current.label}), el('code', {text: name}))
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
        el('div', {}, [nameLine, el('p', {className: 'setting-description', text: setting.description})]),
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
        // The status lists the enabled trackers in the saved execution order, so the Main tab shows a new order straight away
        refreshStatus()
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
            showToast(settingsSavedMessage(savedNames))
        })
        .catch(function (error) {
            const setting = findSetting(name)
            settingRows[name].showError(error.message)
            showToast(`${setting ? setting.label : name} was not saved: ${error.message}`, 'error')
        })
}

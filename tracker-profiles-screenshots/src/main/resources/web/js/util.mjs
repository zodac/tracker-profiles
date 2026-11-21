import {MAXIMUM_TOASTS} from './constants.mjs'

// ---------------------------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------------------------

export function byId(id) {
    return document.getElementById(id)
}

export function hasKey(object, key) {
    return Object.prototype.hasOwnProperty.call(object, key)
}

// Creates an element. Text is always set with textContent, so values from the server are never parsed as HTML
export function el(tag, options, children) {
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

export async function request(method, path, body) {
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

export function showToast(message, kind) {
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

export function pluralise(count, word) {
    return `${count} ${word}${count === 1 ? '' : 's'}`
}

export function formatTime(epochMillis) {
    const date = new Date(epochMillis)
    const pad = function (value, length) {
        return String(value).padStart(length, '0')
    }
    return `${pad(date.getHours(), 2)}:${pad(date.getMinutes(), 2)}:${pad(date.getSeconds(), 2)}.${pad(date.getMilliseconds(), 3)}`
}

// ---------------------------------------------------------------------------------------------------------------
// Two-step confirmation: the first click of a button arms it (its colours inverted, reading 'Confirm'), and only a second click runs its action. Clicking
// anywhere else (or Escape) disarms it. Buttons are found by their key (data-confirm-key), so a button re-rendered while armed stays armed
// ---------------------------------------------------------------------------------------------------------------

let confirmingKey = null

export function hasConfirmation() {
    return confirmingKey !== null
}

export function isConfirming(key) {
    return confirmingKey === key
}

// Shows a button as armed or not, with its usual label (data-confirm-label) when not armed
export function applyConfirmation(button) {
    const confirming = isConfirming(button.dataset.confirmKey)
    button.classList.toggle('confirming', confirming)
    button.textContent = confirming ? 'Confirm' : button.dataset.confirmLabel
}

function updateConfirmButtons() {
    document.querySelectorAll('[data-confirm-key]').forEach(applyConfirmation)
}

// Called on each click of the button: arms it, or runs the action if it is already armed
export function confirmThenRun(key, action) {
    if (isConfirming(key)) {
        clearConfirmation()
        action()
        return
    }
    confirmingKey = key
    updateConfirmButtons()
}

export function clearConfirmation() {
    if (confirmingKey !== null) {
        confirmingKey = null
        updateConfirmButtons()
    }
}

// Disarms the armed button when anything else is clicked
export function clearConfirmationOutside(target) {
    const button = target.closest ? target.closest('[data-confirm-key]') : null
    if (!button || !isConfirming(button.dataset.confirmKey)) {
        clearConfirmation()
    }
}

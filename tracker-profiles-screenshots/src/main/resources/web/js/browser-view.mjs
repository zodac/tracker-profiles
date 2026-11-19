import {BROWSER_VIEW_RETRY_MS, MAXIMUM_PASTED_TEXT_LENGTH} from './constants.mjs'
import {el, request, showToast} from './util.mjs'

// ---------------------------------------------------------------------------------------------------------------
// Live view of a tracker's browser (see BrowserView), which runs in the container, so the host needs no display
// ---------------------------------------------------------------------------------------------------------------

// Each view requests the next frame as soon as it has drawn the previous one (the server waits up to 1s for a newer frame), and stops while the
// page is hidden or the view is not in the page. Frames are drawn on a canvas, since the page's Content-Security-Policy does not allow images
// from 'blob:' URLs
const views = new Set()

// Bit masks of the modifier keys, as used by the Chrome DevTools Protocol
const ALT_MODIFIER = 1
const CTRL_MODIFIER = 2
const META_MODIFIER = 4
const SHIFT_MODIFIER = 8
const MOUSE_BUTTONS = ['left', 'middle', 'right']
const DOUBLE_CLICK_MS = 500
const DOUBLE_CLICK_DISTANCE = 5

function delay(milliseconds) {
    return new Promise(function (resolve) {
        setTimeout(resolve, milliseconds)
    })
}

// An interactive view sends the user's mouse and keyboard input to the browser, which the server only accepts while the tracker is waiting for
// an input
export function createBrowserView(trackerName, interactive) {
    const canvas = el('canvas', {className: 'browser-view-canvas', hidden: true})
    const placeholder = el('div', {className: 'browser-view-placeholder muted', text: 'Loading the browser…'})
    const view = el('div', {
        className: `browser-view${interactive ? ' interactive' : ''}`,
        tabindex: interactive ? '0' : null,
        role: interactive ? 'application' : 'img',
        'aria-label': interactive ? `${trackerName} browser: click to use it, and type while it is focused` : `${trackerName} browser`,
    }, [canvas, placeholder])
    view.trackerName = trackerName
    view.canvas = canvas
    view.placeholder = placeholder
    view.lastFrameId = 0
    view.wanted = false
    view.streaming = false
    view.pendingEvents = []
    view.sending = false
    if (interactive) {
        addInputListeners(view)
    }
    return view
}

export function startBrowserView(view) {
    view.wanted = true
    views.add(view)
    if (!view.streaming && !document.hidden) {
        streamFrames(view)
    }
}

export function stopBrowserView(view) {
    view.wanted = false
    views.delete(view)
}

// Called when the page is shown again, since streaming stops while it is hidden
export function resumeBrowserViews() {
    views.forEach(function (view) {
        if (!view.isConnected) {
            stopBrowserView(view)
        } else if (view.wanted && !view.streaming) {
            streamFrames(view)
        }
    })
}

function showPlaceholder(view, text) {
    view.placeholder.textContent = text
    view.placeholder.hidden = text === null
    // The last frame of a closed browser is not kept, as it may no longer be what the browser shows
    view.canvas.hidden = text !== null
}

async function streamFrames(view) {
    view.streaming = true
    try {
        while (view.wanted && view.isConnected && !document.hidden) {
            await fetchFrame(view)
        }
    } finally {
        view.streaming = false
        if (!view.isConnected) {
            stopBrowserView(view)
        }
    }
}

async function fetchFrame(view) {
    let response
    try {
        response = await fetch(`/api/browser?tracker=${encodeURIComponent(view.trackerName)}&after=${view.lastFrameId}`, {cache: 'no-store'})
    } catch {
        showPlaceholder(view, 'Unable to reach the application, retrying…')
        await delay(BROWSER_VIEW_RETRY_MS)
        return
    }

    if (response.status === 204) {
        return
    }
    if (!response.ok) {
        showPlaceholder(view, response.status === 404 ? 'No browser is open for this tracker' : 'Unable to show the browser, retrying…')
        await delay(BROWSER_VIEW_RETRY_MS)
        return
    }

    try {
        const bitmap = await createImageBitmap(await response.blob())
        if (view.canvas.width !== bitmap.width || view.canvas.height !== bitmap.height) {
            view.canvas.width = bitmap.width
            view.canvas.height = bitmap.height
        }
        view.canvas.getContext('2d').drawImage(bitmap, 0, 0)
        bitmap.close()
        view.lastFrameId = Number(response.headers.get('X-Frame-Id')) || view.lastFrameId
        showPlaceholder(view, null)
    } catch {
        await delay(BROWSER_VIEW_RETRY_MS)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Input, sent in order: events are queued while a request is in progress, then sent together
// ---------------------------------------------------------------------------------------------------------------

function modifiers(event) {
    return (event.altKey ? ALT_MODIFIER : 0)
        | (event.ctrlKey ? CTRL_MODIFIER : 0)
        | (event.metaKey ? META_MODIFIER : 0)
        | (event.shiftKey ? SHIFT_MODIFIER : 0)
}

// The position of the pointer relative to the frame (0 to 1), so it does not depend on the size at which the frame is shown
function position(view, event) {
    const bounds = view.canvas.getBoundingClientRect()
    return {
        x: bounds.width === 0 ? 0 : (event.clientX - bounds.left) / bounds.width,
        y: bounds.height === 0 ? 0 : (event.clientY - bounds.top) / bounds.height,
    }
}

// The text typed by a key: none for a shortcut (unless AltGr is held, which some keyboards report as Ctrl+Alt), '\r' for Enter, as Chrome sends
function keyText(event) {
    const shortcut = (event.ctrlKey || event.metaKey || event.altKey) && !event.getModifierState('AltGraph')
    if (shortcut) {
        return ''
    }
    if (event.key === 'Enter') {
        return '\r'
    }
    return event.key.length === 1 ? event.key : ''
}

function queueInput(view, inputEvent) {
    // Only the latest pointer move is needed, so consecutive moves are replaced rather than queued
    const last = view.pendingEvents[view.pendingEvents.length - 1]
    if (inputEvent.action === 'mouseMoved' && last && last.action === 'mouseMoved') {
        view.pendingEvents[view.pendingEvents.length - 1] = inputEvent
    } else {
        view.pendingEvents.push(inputEvent)
    }
    sendInput(view)
}

function sendInput(view) {
    if (view.sending || view.pendingEvents.length === 0) {
        return
    }

    const events = view.pendingEvents.splice(0, view.pendingEvents.length)
    view.sending = true
    request('POST', `/api/browser/input?tracker=${encodeURIComponent(view.trackerName)}`, {events: events})
        .catch(function (error) {
            // The tracker may have stopped waiting for the input (it continued, or the page changed) while the user was still using the view
            if (!view.inputErrorShown) {
                view.inputErrorShown = true
                showToast(`Unable to send your input to the ${view.trackerName} browser: ${error.message}`, 'error')
            }
        })
        .finally(function () {
            view.sending = false
            sendInput(view)
        })
}

// Pointer events report no click count, so consecutive presses of the same button close together are counted here (as a double or triple click)
function clickCount(view, event) {
    const now = Date.now()
    const last = view.lastPress
    const repeated = last && last.button === event.button && now - last.time < DOUBLE_CLICK_MS
        && Math.abs(last.x - event.clientX) < DOUBLE_CLICK_DISTANCE && Math.abs(last.y - event.clientY) < DOUBLE_CLICK_DISTANCE
    const count = repeated ? Math.min(last.count + 1, 3) : 1
    view.lastPress = {button: event.button, time: now, x: event.clientX, y: event.clientY, count: count}
    return count
}

function mouseInput(view, event, action, count) {
    const point = position(view, event)
    return {
        type: 'mouse',
        action: action,
        x: point.x,
        y: point.y,
        button: action === 'mouseMoved' ? 'none' : (MOUSE_BUTTONS[event.button] || 'none'),
        buttons: event.buttons,
        clickCount: count,
        modifiers: modifiers(event),
    }
}

function keyInput(event, action) {
    return {
        type: 'key',
        action: action,
        key: event.key,
        code: event.code,
        keyCode: event.keyCode,
        location: event.location,
        text: action === 'keyDown' ? keyText(event) : '',
        modifiers: modifiers(event),
    }
}

function isPasteShortcut(event) {
    return (event.ctrlKey || event.metaKey) && !event.altKey && event.key.toLowerCase() === 'v'
}

function addInputListeners(view) {
    // The pointer is captured while a button is held, so a drag that ends outside the view still releases the button in the browser
    view.canvas.addEventListener('pointerdown', function (event) {
        event.preventDefault()
        view.focus()
        view.canvas.setPointerCapture(event.pointerId)
        view.inputErrorShown = false
        queueInput(view, mouseInput(view, event, 'mousePressed', clickCount(view, event)))
    })
    view.canvas.addEventListener('pointerup', function (event) {
        const count = view.lastPress ? view.lastPress.count : 1
        queueInput(view, mouseInput(view, event, 'mouseReleased', count))
    })
    view.canvas.addEventListener('pointermove', function (event) {
        queueInput(view, mouseInput(view, event, 'mouseMoved', 0))
    })
    view.canvas.addEventListener('contextmenu', function (event) {
        event.preventDefault()
    })
    view.canvas.addEventListener('wheel', function (event) {
        event.preventDefault()
        const point = position(view, event)
        // Lines (deltaMode 1) and pages (deltaMode 2) are converted to pixels
        const scale = event.deltaMode === 1 ? 40 : (event.deltaMode === 2 ? 800 : 1)
        queueInput(view, {
            type: 'wheel', x: point.x, y: point.y, deltaX: event.deltaX * scale, deltaY: event.deltaY * scale, modifiers: modifiers(event),
        })
    }, {passive: false})

    // Every key goes to the browser while the view is focused (including Tab, which moves between its fields), except the paste shortcut, so
    // the text is pasted from the user's clipboard
    view.addEventListener('keydown', function (event) {
        if (isPasteShortcut(event)) {
            return
        }
        event.preventDefault()
        queueInput(view, keyInput(event, 'keyDown'))
    })
    view.addEventListener('keyup', function (event) {
        if (isPasteShortcut(event)) {
            return
        }
        event.preventDefault()
        queueInput(view, keyInput(event, 'keyUp'))
    })
    view.addEventListener('paste', function (event) {
        event.preventDefault()
        const text = event.clipboardData ? event.clipboardData.getData('text') : ''
        if (text) {
            queueInput(view, {type: 'text', text: text.substring(0, MAXIMUM_PASTED_TEXT_LENGTH)})
        }
    })
}

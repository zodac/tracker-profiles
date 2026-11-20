import {BROWSER_VIEW_RETRY_MS, BROWSER_VIEW_ZOOM_LEVELS, BROWSER_VIEW_ZOOM_STORAGE_KEY, MAXIMUM_PASTED_TEXT_LENGTH} from './constants.mjs'
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
// Each zoom step changes the size by at least this factor, so zooming in from 'Fit' (at any size) makes a visible difference
const MINIMUM_ZOOM_STEP = 1.1
// The scroll distance (in pixels) of Ctrl+scroll for each zoom level, so a touchpad pinch (many small scrolls) does not zoom too quickly
const WHEEL_ZOOM_DISTANCE = 50
// The height of the frame shown at 'Fit', as a fraction of the window height (outside full screen), as set for .browser-view-screen
const FIT_HEIGHT_FRACTION = 0.75

function delay(milliseconds) {
    return new Promise(function (resolve) {
        setTimeout(resolve, milliseconds)
    })
}

// An interactive view sends the user's mouse and keyboard input to the browser, which the server only accepts while the tracker is waiting for
// an input
export function createBrowserView(trackerName, interactive) {
    const canvas = el('canvas', {className: 'browser-view-canvas fit', hidden: true})
    const placeholder = el('div', {className: 'browser-view-placeholder muted', text: 'Loading the browser…'})
    // The frame is shown in its own scrolled area, and the keys typed while it is focused go to the browser (not those typed in the toolbar)
    const screen = el('div', {
        className: `browser-view-screen${interactive ? ' interactive' : ''}`,
        tabindex: interactive ? '0' : null,
        role: interactive ? 'application' : 'img',
        'aria-label': interactive ? `${trackerName} browser: click to use it, and type while it is focused` : `${trackerName} browser`,
    }, [canvas, placeholder])
    const view = el('div', {className: 'browser-view'}, [createToolbar(), screen])
    view.trackerName = trackerName
    view.interactive = interactive
    view.screen = screen
    view.canvas = canvas
    view.placeholder = placeholder
    view.zoom = savedZoom(interactive)
    view.wheelZoomDistance = 0
    view.lastFrameId = 0
    view.wanted = false
    view.streaming = false
    view.pendingEvents = []
    view.sending = false
    addZoomListeners(view)
    applyZoom(view)
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
        const resized = view.canvas.width !== bitmap.width || view.canvas.height !== bitmap.height
        if (resized) {
            view.canvas.width = bitmap.width
            view.canvas.height = bitmap.height
        }
        view.canvas.getContext('2d').drawImage(bitmap, 0, 0)
        bitmap.close()
        view.lastFrameId = Number(response.headers.get('X-Frame-Id')) || view.lastFrameId
        const firstFrame = view.canvas.hidden
        showPlaceholder(view, null)
        if (resized || firstFrame) {
            applyZoom(view)
        }
    } catch {
        await delay(BROWSER_VIEW_RETRY_MS)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Zoom and full screen, which only change how the frame is shown (the page in the browser is not zoomed, so its screenshots are unchanged)
// ---------------------------------------------------------------------------------------------------------------

function toolbarButton(className, text, title, action) {
    return el('button', {className: `button small ${className}`, type: 'button', text: text, title: title, 'aria-label': title, onclick: action})
}

function createToolbar() {
    const toolbar = el('div', {className: 'browser-view-toolbar'})
    function view() {
        return toolbar.parentElement
    }
    toolbar.append(
        toolbarButton('browser-view-zoom-out', '\u2212', 'Zoom out (Ctrl+-)', function () {
            zoomOut(view())
        }),
        el('span', {className: 'browser-view-zoom muted', 'aria-live': 'polite'}),
        toolbarButton('browser-view-zoom-in', '+', 'Zoom in (Ctrl+=)', function () {
            zoomIn(view())
        }),
        toolbarButton('browser-view-fit', 'Fit', 'Fit the browser to the page (Ctrl+0)', function () {
            setZoom(view(), null)
        }),
        toolbarButton('browser-view-actual', '100%', 'Show the browser at its actual size', function () {
            setZoom(view(), 1)
        }),
        document.fullscreenEnabled ? toolbarButton('browser-view-fullscreen', 'Full screen', 'Show the browser on the whole screen', function () {
            toggleFullscreen(view())
        }) : null,
    )
    return toolbar
}

function savedZoom(interactive) {
    try {
        const zoom = Number(JSON.parse(window.localStorage.getItem(BROWSER_VIEW_ZOOM_STORAGE_KEY) || '{}')[interactive ? 'input' : 'live'])
        return BROWSER_VIEW_ZOOM_LEVELS.includes(zoom) ? zoom : null
    } catch {
        return null
    }
}

function saveZoom(view) {
    try {
        const saved = JSON.parse(window.localStorage.getItem(BROWSER_VIEW_ZOOM_STORAGE_KEY) || '{}')
        saved[view.interactive ? 'input' : 'live'] = view.zoom
        window.localStorage.setItem(BROWSER_VIEW_ZOOM_STORAGE_KEY, JSON.stringify(saved))
    } catch {
        // Storage is unavailable, so the zoom is only kept for this view
    }
}

function isFullscreen(view) {
    return document.fullscreenElement === view
}

// The zoom at which the whole frame fits the page (never above 1, so a small frame is not enlarged)
function fitZoom(view) {
    const canvas = view.canvas
    if (canvas.hidden || canvas.width === 0 || canvas.height === 0) {
        return 1
    }
    const height = isFullscreen(view) ? view.screen.clientHeight : window.innerHeight * FIT_HEIGHT_FRACTION
    return Math.min(1, view.screen.clientWidth / canvas.width, height / canvas.height)
}

function currentZoom(view) {
    return view.zoom === null ? fitZoom(view) : view.zoom
}

function zoomIn(view, anchor) {
    const current = currentZoom(view)
    const next = BROWSER_VIEW_ZOOM_LEVELS.find(function (zoom) {
        return zoom >= current * MINIMUM_ZOOM_STEP
    })
    if (next !== undefined) {
        setZoom(view, next, anchor)
    }
}

// Zooming out below the size that fits the page shows it fitted instead, since the frame would only be smaller
function zoomOut(view, anchor) {
    const current = currentZoom(view)
    const next = BROWSER_VIEW_ZOOM_LEVELS.findLast(function (zoom) {
        return zoom <= current / MINIMUM_ZOOM_STEP
    })
    setZoom(view, next === undefined || next <= fitZoom(view) ? null : next, anchor)
}

// The point under the pointer (or the middle of the view) stays in place as the zoom changes
function setZoom(view, zoom, anchor) {
    const screen = view.screen
    const before = view.canvas.getBoundingClientRect()
    const bounds = screen.getBoundingClientRect()
    const anchorX = anchor ? anchor.clientX : bounds.left + (screen.clientWidth / 2)
    const anchorY = anchor ? anchor.clientY : bounds.top + (screen.clientHeight / 2)
    const fractionX = before.width === 0 ? 0.5 : (anchorX - before.left) / before.width
    const fractionY = before.height === 0 ? 0.5 : (anchorY - before.top) / before.height

    view.zoom = zoom
    applyZoom(view)
    saveZoom(view)
    if (zoom !== null) {
        const after = view.canvas.getBoundingClientRect()
        screen.scrollLeft += after.left + (fractionX * after.width) - anchorX
        screen.scrollTop += after.top + (fractionY * after.height) - anchorY
    }
}

// At 'Fit' the frame is scaled down by the stylesheet (so it follows the size of the page), otherwise it is given its zoomed size, and scrolls
function applyZoom(view) {
    const canvas = view.canvas
    canvas.classList.toggle('fit', view.zoom === null)
    canvas.style.width = view.zoom === null ? '' : `${Math.round(canvas.width * view.zoom)}px`
    canvas.style.height = view.zoom === null ? '' : `${Math.round(canvas.height * view.zoom)}px`
    updateZoomLabel(view)
}

function updateZoomLabel(view) {
    const percentage = `${Math.round(currentZoom(view) * 100)}%`
    view.querySelector('.browser-view-zoom').textContent = view.zoom === null ? `Fit (${percentage})` : percentage
    view.querySelector('.browser-view-fit').disabled = view.zoom === null
    view.querySelector('.browser-view-actual').disabled = view.zoom === 1
    view.querySelector('.browser-view-zoom-in').disabled = currentZoom(view) >= BROWSER_VIEW_ZOOM_LEVELS[BROWSER_VIEW_ZOOM_LEVELS.length - 1]
    view.querySelector('.browser-view-zoom-out').disabled = view.zoom === null
}

function toggleFullscreen(view) {
    if (isFullscreen(view)) {
        document.exitFullscreen().catch(function () {
            // Already left
        })
        return
    }
    view.requestFullscreen().then(function () {
        if (view.interactive) {
            view.screen.focus({preventScroll: true})
        }
    }).catch(function (error) {
        showToast(`Unable to show the browser on the whole screen: ${error.message}`, 'error')
    })
}

function isZoomShortcut(event) {
    return (event.ctrlKey || event.metaKey) && !event.altKey && ['=', '+', '-', '_', '0'].includes(event.key)
}

function zoomWithShortcut(view, event) {
    if (event.key === '0') {
        setZoom(view, null)
    } else if (event.key === '-' || event.key === '_') {
        zoomOut(view)
    } else {
        zoomIn(view)
    }
}

function addZoomListeners(view) {
    // Ctrl+scroll (or a touchpad pinch) zooms the view, rather than the page or the browser
    view.screen.addEventListener('wheel', function (event) {
        if (!event.ctrlKey && !event.metaKey) {
            return
        }
        event.preventDefault()
        view.wheelZoomDistance += event.deltaMode === 0 ? event.deltaY : event.deltaY * WHEEL_ZOOM_DISTANCE
        if (Math.abs(view.wheelZoomDistance) >= WHEEL_ZOOM_DISTANCE) {
            if (view.wheelZoomDistance < 0) {
                zoomIn(view, event)
            } else {
                zoomOut(view, event)
            }
            view.wheelZoomDistance = 0
        }
    }, {passive: false})
    view.addEventListener('fullscreenchange', function () {
        const button = view.querySelector('.browser-view-fullscreen')
        button.textContent = isFullscreen(view) ? 'Exit full screen' : 'Full screen'
        button.title = isFullscreen(view) ? 'Exit full screen (Esc)' : 'Show the browser on the whole screen'
        button.setAttribute('aria-label', button.title)
        updateZoomLabel(view)
    })
}

// The size that fits the page changes with the window
window.addEventListener('resize', function () {
    views.forEach(updateZoomLabel)
})

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
        view.screen.focus({preventScroll: true})
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
        // Ctrl+scroll zooms the view instead (see addZoomListeners)
        if (event.ctrlKey || event.metaKey) {
            return
        }
        event.preventDefault()
        const point = position(view, event)
        // Lines (deltaMode 1) and pages (deltaMode 2) are converted to pixels
        const scale = event.deltaMode === 1 ? 40 : (event.deltaMode === 2 ? 800 : 1)
        queueInput(view, {
            type: 'wheel', x: point.x, y: point.y, deltaX: event.deltaX * scale, deltaY: event.deltaY * scale, modifiers: modifiers(event),
        })
    }, {passive: false})

    // Every key goes to the browser while the view is focused (including Tab, which moves between its fields), except the paste shortcut, so
    // the text is pasted from the user's clipboard, and the zoom shortcuts, which zoom the view
    view.screen.addEventListener('keydown', function (event) {
        if (isPasteShortcut(event)) {
            return
        }
        event.preventDefault()
        if (isZoomShortcut(event)) {
            zoomWithShortcut(view, event)
            return
        }
        queueInput(view, keyInput(event, 'keyDown'))
    })
    view.screen.addEventListener('keyup', function (event) {
        if (isPasteShortcut(event)) {
            return
        }
        event.preventDefault()
        if (isZoomShortcut(event)) {
            return
        }
        queueInput(view, keyInput(event, 'keyUp'))
    })
    view.screen.addEventListener('paste', function (event) {
        event.preventDefault()
        const text = event.clipboardData ? event.clipboardData.getData('text') : ''
        if (text) {
            queueInput(view, {type: 'text', text: text.substring(0, MAXIMUM_PASTED_TEXT_LENGTH)})
        }
    })
}

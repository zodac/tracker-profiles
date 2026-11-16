import {byId, el, pluralise} from './util.mjs'

// ---------------------------------------------------------------------------------------------------------------
// Screenshots of each tracker (thumbnails on the Main tab, and the latest successful screenshots on the Trackers tab, opened in a gallery), and
// downloads
// ---------------------------------------------------------------------------------------------------------------

// The screenshots shown in the gallery, the index of the one being viewed, and whether they are the latest successful screenshots of the
// tracker (from any execution) rather than those of the current execution
const gallery = {trackerName: null, screenshots: [], index: 0, latest: false}

// 'savedAt' is included so a screenshot replaced on a retry is fetched again (otherwise the image is cached by the browser)
function screenshotUrl(trackerName, screenshot, option, latest) {
    const parameters = new URLSearchParams({tracker: trackerName, id: screenshot.id, v: String(screenshot.savedAt)})
    if (option) {
        parameters.set(option, 'true')
    }
    if (latest) {
        parameters.set('latest', 'true')
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
export function updateTrackerScreenshots(container, tracker) {
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

// The latest profile screenshots of every tracker that has succeeded (in any execution) can be downloaded together
export function updateExecutionDownload(status) {
    const count = status.downloadableScreenshots
    const link = byId('download-screenshots')
    link.hidden = count === 0
    link.querySelector('.download-text').textContent = count > 1 ? `Download all (${count})` : 'Download screenshot'
    link.title = count > 1
        ? `Download the latest ${count} screenshots of the ${pluralise(status.downloadableTrackers, 'successful tracker')}`
            + ' (from any execution) as a .zip file'
        : 'Download the latest screenshot of the successful tracker'
}

export function openGallery(trackerName, screenshots, index, latest) {
    gallery.trackerName = trackerName
    gallery.screenshots = screenshots
    gallery.latest = Boolean(latest)
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
    fullSize.href = screenshotUrl(gallery.trackerName, screenshot, null, gallery.latest)
    fullSize.title = `Open ${screenshot.fileName} at full size in a new tab`
    const download = byId('gallery-download')
    download.href = screenshotUrl(gallery.trackerName, screenshot, 'download', gallery.latest)
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
    image.src = screenshotUrl(gallery.trackerName, screenshot, 'preview', gallery.latest)
    viewport.replaceChildren(image)
}

export function setUpGallery() {
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

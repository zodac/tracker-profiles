import {resumeBrowserViews} from './js/browser-view.mjs'
import {DARK_SCHEME_QUERY, HIDDEN_POLL_INTERVAL_MS} from './js/constants.mjs'
import {fetchLogs, trackerTiming} from './js/execution-trackers.mjs'
import {
    cancelRun,
    isActive,
    latestStatus,
    refreshStatus,
    runDescription,
    scheduleStatusRefresh,
    startRun,
    statusTimer,
    updateInputTimers,
} from './js/run.mjs'
import {setUpGallery} from './js/screenshots.mjs'
import {unsavedSettings} from './js/settings.mjs'
import {showTab} from './js/tabs.mjs'
import {toggleTheme, updateThemeToggle} from './js/theme-toggle.mjs'
import {
    changeSelectedTrackers,
    clearSelection,
    importTrackers,
    loadTrackers,
    releasePinnedTrackersOutside,
    renderTrackers,
    selectedTrackers,
    trackerDrafts,
} from './js/trackers.mjs'
import {byId, showToast} from './js/util.mjs'

// ---------------------------------------------------------------------------------------------------------------
// Start up
// ---------------------------------------------------------------------------------------------------------------

document.addEventListener('DOMContentLoaded', function () {
    updateThemeToggle()
    DARK_SCHEME_QUERY.addEventListener('change', updateThemeToggle)
    byId('theme-toggle').addEventListener('click', toggleTheme)
    byId('start-button').addEventListener('click', function () {
        startRun(false)
    })
    byId('start-failed-button').addEventListener('click', function () {
        startRun(true)
    })
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
    document.addEventListener('visibilitychange', function () {
        if (document.hidden) {
            scheduleStatusRefresh(HIDDEN_POLL_INTERVAL_MS)
        } else {
            clearTimeout(statusTimer)
            refreshStatus()
            resumeBrowserViews()
        }
    })
    setInterval(function () {
        if (document.hidden) {
            return
        }
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

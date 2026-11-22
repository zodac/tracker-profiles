import {resumeBrowserViews} from './js/browser-view.mjs'
import {DARK_SCHEME_QUERY, HIDDEN_POLL_INTERVAL_MS} from './js/constants.mjs'
import {trackerTiming} from './js/execution-trackers.mjs'
import {
    cancelRun,
    isActive,
    latestStatus,
    refreshStatus,
    runDescription,
    scheduleStatusRefresh,
    startRun,
    statusTimer,
    togglePendingInputs,
} from './js/run.mjs'
import {closeDownloadMenu, setUpGallery} from './js/screenshots.mjs'
import {
    chooseBackupToRestore,
    closeBackupMenu,
    onBackupMenuToggled,
    restoreBackup,
    unsavedSettings,
    updateBackupDownload,
} from './js/settings.mjs'
import {showTab} from './js/tabs.mjs'
import {toggleTheme, updateThemeToggle} from './js/theme-toggle.mjs'
import {
    changeSelectedTrackers,
    clearSelection,
    importTrackers,
    loadTrackers,
    releasePinnedTrackersOutside,
    renderTrackers,
    selectAllTrackers,
    selectedTrackers,
    trackerDrafts,
} from './js/trackers.mjs'
import {byId, clearConfirmation, clearConfirmationOutside, hasConfirmation, showToast} from './js/util.mjs'

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
    byId('pending-inputs').addEventListener('toggle', togglePendingInputs)
    setUpGallery()
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
        if (event.key === 'Escape' && hasConfirmation()) {
            clearConfirmation()
        } else if (event.key === 'Escape' && byId('download-menu').open) {
            closeDownloadMenu()
        } else if (event.key === 'Escape' && byId('backup-menu').open) {
            closeBackupMenu()
        } else if (event.key === 'Escape' && selectedTrackers.size > 0) {
            clearSelection()
        } else if (isSelectAllShortcut(event)) {
            event.preventDefault()
            selectAllTrackers()
        }
    })
    document.addEventListener('pointerdown', function (event) {
        clearConfirmationOutside(event.target)
        releasePinnedTrackersOutside(event.target)
        if (!byId('download-menu').contains(event.target)) {
            closeDownloadMenu()
        }
        if (!byId('backup-menu').contains(event.target)) {
            closeBackupMenu()
        }
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
    byId('backup-menu').addEventListener('toggle', onBackupMenuToggled)
    byId('backup-credentials').addEventListener('change', updateBackupDownload)
    byId('backup-screenshots').addEventListener('change', updateBackupDownload)
    byId('backup-download').addEventListener('click', closeBackupMenu)
    updateBackupDownload()
    byId('backup-restore').addEventListener('click', chooseBackupToRestore)
    byId('backup-file').addEventListener('change', function () {
        const file = byId('backup-file').files[0]
        if (file) {
            restoreBackup(file)
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

// Ctrl/Cmd+A on the Trackers tab selects trackers, unless typing in a field (which selects its text) or a dialog is open
function isSelectAllShortcut(event) {
    if (!(event.ctrlKey || event.metaKey) || event.altKey || event.shiftKey || event.key.toLowerCase() !== 'a') {
        return false
    }
    const typing = event.target.closest && event.target.closest('input:not([type="checkbox"]):not([type="radio"]), textarea, select, [contenteditable]')
    return !byId('tab-trackers').hidden && !typing && !document.querySelector('dialog[open]')
}

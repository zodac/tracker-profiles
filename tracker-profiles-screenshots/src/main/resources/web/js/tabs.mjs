import {loadSettings} from './settings.mjs'
import {loadTrackers} from './trackers.mjs'
import {hasKey, showToast} from './util.mjs'

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

export function showTab() {
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

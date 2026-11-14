// Applies the theme before the page is rendered, to avoid a flash of the wrong theme: the one saved by the user (see js/theme-toggle.mjs), or
// otherwise the operating system preference, which is followed while the page is open. style.css only styles the 'data-theme' attribute
(function () {
    'use strict'

    const darkScheme = window.matchMedia('(prefers-color-scheme: dark)')

    function savedTheme() {
        try {
            const theme = window.localStorage.getItem('tracker-profiles-theme')
            return theme === 'light' || theme === 'dark' ? theme : null
        } catch {
            // Storage is unavailable (e.g. blocked by the browser), so follow the operating system preference
            return null
        }
    }

    function applyTheme() {
        document.documentElement.dataset.theme = savedTheme() || (darkScheme.matches ? 'dark' : 'light')
    }

    applyTheme()
    darkScheme.addEventListener('change', applyTheme)
}())

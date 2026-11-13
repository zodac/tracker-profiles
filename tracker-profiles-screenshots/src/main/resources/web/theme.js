// Applies the theme saved by the user (see app.js) before the page is rendered, to avoid a flash of the wrong theme
// With no saved theme, the operating system preference is used (through the 'prefers-color-scheme' media query in style.css)
(function () {
    'use strict'

    try {
        const theme = window.localStorage.getItem('tracker-profiles-theme')
        if (theme === 'light' || theme === 'dark') {
            document.documentElement.dataset.theme = theme
        }
    } catch {
        // Storage is unavailable (e.g. blocked by the browser), so follow the operating system preference
    }
}())

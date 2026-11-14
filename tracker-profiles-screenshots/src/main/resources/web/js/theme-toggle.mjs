import {THEME_STORAGE_KEY} from './constants.mjs'
import {byId} from './util.mjs'

// ---------------------------------------------------------------------------------------------------------------
// Theme (the saved choice is applied before the first paint by theme.js)
// ---------------------------------------------------------------------------------------------------------------

// Always set by theme.js
function currentTheme() {
    return document.documentElement.dataset.theme
}

export function updateThemeToggle() {
    const label = currentTheme() === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'
    const toggle = byId('theme-toggle')
    toggle.setAttribute('aria-label', label)
    toggle.title = label
}

export function toggleTheme() {
    const theme = currentTheme() === 'dark' ? 'light' : 'dark'
    document.documentElement.dataset.theme = theme
    try {
        window.localStorage.setItem(THEME_STORAGE_KEY, theme)
    } catch {
        // Storage is unavailable, so the choice only applies until the page is closed
    }
    updateThemeToggle()
}

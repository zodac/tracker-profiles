if (!window.__undoRedaction) {
    window.__undoRedaction = function () {
        document.querySelectorAll('[data-redact-overlay]').forEach(function (el) {
            el.remove()
        })
        document.querySelectorAll('[data-redact-wrapped]').forEach(function (span) {
            const parent = span.parentNode
            if (parent) {
                parent.replaceChild(document.createTextNode(span.textContent), span)
            }
        })
        document.querySelectorAll('[data-redact-blurred]').forEach(function (el) {
            el.style.filter = el.getAttribute('data-redact-blurred')
            el.removeAttribute('data-redact-blurred')
        })
    }
}

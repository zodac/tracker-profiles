if (!window.__redactElement) {
    window.__redactElement = function (element, bufferLeft, bufferUp, bufferRight, bufferDown, bgColor, textColor, label, blurDef, redactionType) {
        if (redactionType === 'blur') {
            if (!element.hasAttribute('data-redact-blurred')) {
                element.setAttribute('data-redact-blurred', element.style.filter || '')
            }
            element.style.filter = blurDef
        } else {
            const bounding_rectangle = element.getBoundingClientRect()
            const scroll_top = window.pageYOffset || document.documentElement.scrollTop
            const scroll_left = window.pageXOffset || document.documentElement.scrollLeft
            const computed_style = window.getComputedStyle(element)

            const overlay = document.createElement('div')
            overlay.setAttribute('data-redact-overlay', '')
            overlay.style.position = 'absolute'
            overlay.style.left = `${bounding_rectangle.left + scroll_left - bufferLeft}px`
            overlay.style.top = `${bounding_rectangle.top + scroll_top - bufferUp}px`
            overlay.style.width = `${bounding_rectangle.width + bufferLeft + bufferRight}px`
            overlay.style.height = `${bounding_rectangle.height + bufferUp + bufferDown}px`
            overlay.style.backgroundColor = bgColor
            overlay.style.zIndex = '9999'
            overlay.style.pointerEvents = 'none'
            overlay.style.boxSizing = 'border-box'
            overlay.style.display = 'flex'
            overlay.style.alignItems = 'center'
            overlay.style.justifyContent = 'center'
            overlay.style.color = textColor
            overlay.style.fontSize = computed_style.fontSize
            overlay.style.fontFamily = computed_style.fontFamily
            overlay.style.fontWeight = 'bold'
            overlay.textContent = label
            overlay.id = `redact-${Date.now()}`
            document.body.appendChild(overlay)
        }
    }
}

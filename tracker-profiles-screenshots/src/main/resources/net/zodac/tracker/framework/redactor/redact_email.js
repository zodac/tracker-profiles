if (!window.__redactEmail) {
    window.__redactEmail = function (element, bufferLeft, bufferUp, bufferRight, bufferDown, bgColor, textColor, label, blurDef, redactionType) {
        const rc = window.__redactCommon
        const email_regex = /[a-zA-Z0-9._+\-*]+@[a-zA-Z0-9.\-*]+\.[a-zA-Z*]{2,}/g
        const ctx = {
            scroll_top: window.pageYOffset || document.documentElement.scrollTop,
            scroll_left: window.pageXOffset || document.documentElement.scrollLeft,
            computed_style: window.getComputedStyle(element),
            overlay_ids: [],
            bufferLeft, bufferUp, bufferRight, bufferDown, bgColor, textColor, label, blurDef, redactionType
        }
        const counter = { value: 0 }

        rc.applyRegex(element, email_regex, 'redact-email', counter)

        const target_spans = element.querySelectorAll('.redact-email')
        for (let i = 0; i < target_spans.length; i++) {
            rc.applyRedaction(target_spans[i], ctx)
        }

        // Also check 'value' attributes on the element and its descendants
        const value_elements = [element].concat(Array.from(element.querySelectorAll('[value]')))
        for (let e = 0; e < value_elements.length; e++) {
            const val = value_elements[e].getAttribute('value')
            email_regex.lastIndex = 0
            if (val && email_regex.test(val)) {
                rc.applyRedaction(value_elements[e], ctx)
            }
        }
    }
}

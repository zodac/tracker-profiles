if (!window.__redactPasskey) {
    window.__redactPasskey = function (element, bufferLeft, bufferUp, bufferRight, bufferDown, bgColor, textColor, label, prefixAlternation, blurDef, redactionType) {
        const rc = window.__redactCommon
        const ctx = {
            scroll_top: window.pageYOffset || document.documentElement.scrollTop,
            scroll_left: window.pageXOffset || document.documentElement.scrollLeft,
            computed_style: window.getComputedStyle(element),
            overlay_ids: [],
            bufferLeft, bufferUp, bufferRight, bufferDown, bgColor, textColor, label, blurDef, redactionType
        }

        function find_sensitive_spans(text_nodes, prefix_len) {
            const spans = []
            let chars_seen = 0
            let past_prefix = false
            for (let i = 0; i < text_nodes.length; i++) {
                const tn = text_nodes[i]
                const tn_text = tn.textContent
                const tn_len = tn_text.length
                if (past_prefix) {
                    const span = document.createElement('span')
                    span.setAttribute('data-redact-wrapped', '')
                    span.textContent = tn_text
                    tn.parentNode.replaceChild(span, tn)
                    spans.push(span)
                } else if (chars_seen + tn_len <= prefix_len) {
                    chars_seen += tn_len
                } else {
                    const split_pos = prefix_len - chars_seen
                    const parent = tn.parentNode
                    if (split_pos > 0) {
                        parent.insertBefore(document.createTextNode(tn_text.slice(0, split_pos)), tn)
                    }
                    const sensitive_span = document.createElement('span')
                    sensitive_span.setAttribute('data-redact-wrapped', '')
                    sensitive_span.textContent = tn_text.slice(split_pos)
                    parent.replaceChild(sensitive_span, tn)
                    spans.push(sensitive_span)
                    past_prefix = true
                }
            }
            return spans
        }

        const prefix_regex = new RegExp(`^\\s*(${prefixAlternation})\\s*:\\s*`, 'i')
        const prefix_match = prefix_regex.exec(element.textContent)

        if (!prefix_match) {
            rc.applyRedaction(element, ctx)
        } else {
            const sensitive_spans = find_sensitive_spans(rc.collectTextNodes(element), prefix_match[0].length)
            for (let i = 0; i < sensitive_spans.length; i++) {
                rc.applyRedaction(sensitive_spans[i], ctx, i === 0)
            }
        }

        // Also check 'value' attributes on the element and its descendants.
        // Skipped when the whole element was blur-redacted, as value attributes within it are already covered.
        if (prefix_match || redactionType !== 'blur') {
            const value_elements = [element].concat(Array.from(element.querySelectorAll('[value]')))
            for (let e = 0; e < value_elements.length; e++) {
                if (value_elements[e].getAttribute('value')) {
                    rc.applyRedaction(value_elements[e], ctx)
                }
            }
        }
    }
}

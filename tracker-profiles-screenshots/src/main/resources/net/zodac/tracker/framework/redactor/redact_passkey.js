if (!window.__redactPasskey) {
    window.__redactPasskey = function (element, bufferLeft, bufferUp, bufferRight, bufferDown, bgColor, label, prefixAlternation, blurDef, redactionType) {
        function collect_text_nodes(el) {
            const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null)
            const text_nodes = []
            let node
            while ((node = walker.nextNode())) {
                text_nodes.push(node)
            }
            return text_nodes
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

        const scroll_top = window.pageYOffset || document.documentElement.scrollTop
        const scroll_left = window.pageXOffset || document.documentElement.scrollLeft
        const computed_style = window.getComputedStyle(element)
        const overlay_ids = []

        function apply_box(bounding_rectangle, show_label) {
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
            overlay.style.color = 'white'
            overlay.style.fontSize = computed_style.fontSize
            overlay.style.fontFamily = computed_style.fontFamily
            overlay.style.fontWeight = 'bold'
            if (show_label) {
                overlay.textContent = label
            }
            overlay.id = `redact-${Date.now()}-${overlay_ids.length}`
            document.body.appendChild(overlay)
            overlay_ids.push(overlay.id)
        }

        function apply_redaction(target_element, show_label) {
            if (redactionType === 'blur') {
                if (!target_element.hasAttribute('data-redact-blurred')) {
                    target_element.setAttribute('data-redact-blurred', target_element.style.filter || '')
                }
                target_element.style.filter = blurDef
            } else {
                apply_box(target_element.getBoundingClientRect(), show_label)
            }
        }

        if (!prefix_match) {
            apply_redaction(element, true)
        } else {
            const sensitive_spans = find_sensitive_spans(collect_text_nodes(element), prefix_match[0].length)
            for (let i = 0; i < sensitive_spans.length; i++) {
                apply_redaction(sensitive_spans[i], i === 0)
            }
        }

        // Also check 'value' attributes on the element and its descendants.
        // Skipped when the whole element was blur-redacted, as value attributes within it are already covered.
        if (prefix_match || redactionType !== 'blur') {
            const value_elements = [element].concat(Array.from(element.querySelectorAll('[value]')))
            for (let e = 0; e < value_elements.length; e++) {
                if (value_elements[e].getAttribute('value')) {
                    apply_redaction(value_elements[e], true)
                }
            }
        }
    }
}

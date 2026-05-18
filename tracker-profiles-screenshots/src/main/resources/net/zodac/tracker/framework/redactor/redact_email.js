if (!window.__redactEmail) {
    window.__redactEmail = function (element, bufferLeft, bufferUp, bufferRight, bufferDown, bgColor, textColor, label, blurDef, redactionType) {
        const email_regex = /[a-zA-Z0-9._+\-*]+@[a-zA-Z0-9.\-*]+\.[a-zA-Z*]{2,}/g
        let counter = 0

        function collect_text_nodes(el) {
            const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null)
            const text_nodes = []
            let node
            while ((node = walker.nextNode())) {
                text_nodes.push(node)
            }
            return text_nodes
        }

        function wrap_matches_in_text_node(text_node, regex, class_name) {
            const text = text_node.textContent
            regex.lastIndex = 0
            if (!regex.test(text)) {
                return
            }

            regex.lastIndex = 0
            const fragment = document.createDocumentFragment()
            let last_index = 0
            let match
            while ((match = regex.exec(text)) !== null) {
                if (match.index > last_index) {
                    fragment.appendChild(document.createTextNode(text.slice(last_index, match.index)))
                }
                const span = document.createElement('span')
                span.setAttribute('data-redact-wrapped', '')
                span.className = class_name
                span.dataset.index = String(counter++)
                span.textContent = match[0]
                fragment.appendChild(span)
                last_index = match.index + match[0].length
            }
            if (last_index < text.length) {
                fragment.appendChild(document.createTextNode(text.slice(last_index)))
            }
            text_node.parentNode.replaceChild(fragment, text_node)
        }

        function apply_regex(el, regex, class_name) {
            const text_nodes = collect_text_nodes(el)
            for (let t = 0; t < text_nodes.length; t++) {
                wrap_matches_in_text_node(text_nodes[t], regex, class_name)
            }
        }

        const scroll_top = window.pageYOffset || document.documentElement.scrollTop
        const scroll_left = window.pageXOffset || document.documentElement.scrollLeft
        const computed_style = window.getComputedStyle(element)
        const overlay_ids = []

        function apply_box(bounding_rectangle) {
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
            overlay.id = `redact-${Date.now()}-${overlay_ids.length}`
            document.body.appendChild(overlay)
            overlay_ids.push(overlay.id)
        }

        function apply_redaction(target_element) {
            if (redactionType === 'blur') {
                if (!target_element.hasAttribute('data-redact-blurred')) {
                    target_element.setAttribute('data-redact-blurred', target_element.style.filter || '')
                }
                target_element.style.filter = blurDef
            } else {
                apply_box(target_element.getBoundingClientRect())
            }
        }

        apply_regex(element, email_regex, 'redact-email')

        const target_spans = element.querySelectorAll('.redact-email')
        for (let i = 0; i < target_spans.length; i++) {
            apply_redaction(target_spans[i])
        }

        // Also check 'value' attributes on the element and its descendants
        const value_elements = [element].concat(Array.from(element.querySelectorAll('[value]')))
        for (let e = 0; e < value_elements.length; e++) {
            const val = value_elements[e].getAttribute('value')
            email_regex.lastIndex = 0
            if (val && email_regex.test(val)) {
                apply_redaction(value_elements[e])
            }
        }
    }
}

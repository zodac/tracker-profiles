if (!window.__redactCommon) {
    window.__redactCommon = {
        collectTextNodes: function (el) {
            const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null)
            const text_nodes = []
            let node
            while ((node = walker.nextNode())) {
                text_nodes.push(node)
            }
            return text_nodes
        },

        wrapMatchesInTextNode: function (text_node, regex, class_name, counter) {
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
                span.dataset.index = String(counter.value++)
                span.textContent = match[0]
                fragment.appendChild(span)
                last_index = match.index + match[0].length
            }
            if (last_index < text.length) {
                fragment.appendChild(document.createTextNode(text.slice(last_index)))
            }
            text_node.parentNode.replaceChild(fragment, text_node)
        },

        applyRegex: function (el, regex, class_name, counter) {
            const text_nodes = this.collectTextNodes(el)
            for (let t = 0; t < text_nodes.length; t++) {
                this.wrapMatchesInTextNode(text_nodes[t], regex, class_name, counter)
            }
        },

        applyBox: function (bounding_rectangle, ctx, show_label) {
            const overlay = document.createElement('div')
            overlay.setAttribute('data-redact-overlay', '')
            overlay.style.position = 'absolute'
            overlay.style.left = `${bounding_rectangle.left + ctx.scroll_left - ctx.bufferLeft}px`
            overlay.style.top = `${bounding_rectangle.top + ctx.scroll_top - ctx.bufferUp}px`
            overlay.style.width = `${bounding_rectangle.width + ctx.bufferLeft + ctx.bufferRight}px`
            overlay.style.height = `${bounding_rectangle.height + ctx.bufferUp + ctx.bufferDown}px`
            overlay.style.backgroundColor = ctx.bgColor
            overlay.style.zIndex = '9999'
            overlay.style.pointerEvents = 'none'
            overlay.style.boxSizing = 'border-box'
            overlay.style.display = 'flex'
            overlay.style.alignItems = 'center'
            overlay.style.justifyContent = 'center'
            overlay.style.color = ctx.textColor
            overlay.style.fontSize = ctx.computed_style.fontSize
            overlay.style.fontFamily = ctx.computed_style.fontFamily
            overlay.style.fontWeight = 'bold'
            if (show_label !== false) {
                overlay.textContent = ctx.label
            }
            overlay.id = `redact-${Date.now()}-${ctx.overlay_ids.length}`
            document.body.appendChild(overlay)
            ctx.overlay_ids.push(overlay.id)
        },

        applyRedaction: function (target_element, ctx, show_label) {
            if (ctx.redactionType === 'blur') {
                if (!target_element.hasAttribute('data-redact-blurred')) {
                    target_element.setAttribute('data-redact-blurred', target_element.style.filter || '')
                }
                target_element.style.filter = ctx.blurDef
            } else {
                this.applyBox(target_element.getBoundingClientRect(), ctx, show_label)
            }
        }
    }
}

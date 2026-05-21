if (!window.__redactIpAddress) {
    window.__redactIpAddress = function (element, bufferLeft, bufferUp, bufferRight, bufferDown, bgColor, textColor, label, blurDef, redactionType) {
        const rc = window.__redactCommon
        const ipv4_regex = /((25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)/g
        const ipv4_masked_regex = /((25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)\.){2}x\.x/g
        const ipv6_full_regex = /([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}/g
        const ipv6_partial_regex = /([0-9a-fA-F]{4}:){3,7}[0-9a-fA-F]{0,4}/g
        const ctx = {
            scroll_top: window.pageYOffset || document.documentElement.scrollTop,
            scroll_left: window.pageXOffset || document.documentElement.scrollLeft,
            computed_style: window.getComputedStyle(element),
            overlay_ids: [],
            bufferLeft, bufferUp, bufferRight, bufferDown, bgColor, textColor, label, blurDef, redactionType
        }
        const counter = { value: 0 }

        function matches_any_ip_regex(val) {
            ipv4_masked_regex.lastIndex = 0
            ipv6_full_regex.lastIndex = 0
            ipv6_partial_regex.lastIndex = 0
            ipv4_regex.lastIndex = 0
            return ipv4_masked_regex.test(val) || ipv6_full_regex.test(val) || ipv6_partial_regex.test(val) || ipv4_regex.test(val)
        }

        // Order matters: check masked IPv4 before regular IPv4 to avoid partial matches
        rc.applyRegex(element, ipv4_masked_regex, 'redact-ip', counter)
        rc.applyRegex(element, ipv6_full_regex, 'redact-ip', counter)
        rc.applyRegex(element, ipv6_partial_regex, 'redact-ip', counter)
        rc.applyRegex(element, ipv4_regex, 'redact-ip', counter)

        const target_spans = element.querySelectorAll('.redact-ip')
        for (let i = 0; i < target_spans.length; i++) {
            rc.applyRedaction(target_spans[i], ctx)
        }

        // Also check 'value' attributes on the element and its descendants
        const value_elements = [element].concat(Array.from(element.querySelectorAll('[value]')))
        for (let e = 0; e < value_elements.length; e++) {
            const val = value_elements[e].getAttribute('value')
            if (val && matches_any_ip_regex(val)) {
                rc.applyRedaction(value_elements[e], ctx)
            }
        }
    }
}

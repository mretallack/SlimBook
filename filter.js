// SlimBook Content Filter v1.1
// Removes ads, stories, suggestions, group/page suggestions from web.facebook.com
(function() {
    var highlightMode = window.__slimbook_highlight || false;

    function hide(el, type) {
        if (!el || el.getAttribute('data-filtered')) return;
        if (highlightMode) {
            el.style.outline = '3px solid red';
            el.style.opacity = '0.4';
        } else {
            el.style.setProperty('display', 'none', 'important');
        }
        el.setAttribute('data-filtered', type);
    }

    // Hide and also collapse any parent with inline height (for Reels/Stories gaps)
    function hideCollapse(el, type) {
        if (!el || el.getAttribute('data-filtered')) return;
        hide(el, type);
        if (highlightMode) return;
        // Walk up parents and zero any inline height styles
        var p = el.parentElement;
        for (var i = 0; i < 5; i++) {
            if (!p || p === document.body) break;
            var s = p.getAttribute('style') || '';
            if (s.indexOf('height') !== -1) {
                p.style.setProperty('height', '0px', 'important');
                p.style.setProperty('min-height', '0', 'important');
                p.style.setProperty('overflow', 'hidden', 'important');
            }
            p = p.parentElement;
        }
    }

    // Walk up to find a sizeable parent container
    function findContainer(el, minH, maxH) {
        var parent = el;
        for (var i = 0; i < 25; i++) {
            if (!parent.parentElement) break;
            parent = parent.parentElement;
            var h = parent.getBoundingClientRect().height;
            if (h >= minH && h <= maxH) {
                return parent;
            }
        }
        return null;
    }

    function removeUnwanted() {
        // Only filter on the home feed
        var path = window.location.pathname;
        if (path !== '/' && path !== '/home.php' && path !== '/index.php') return;

        // Scan all elements that could contain marker text
        var els = document.querySelectorAll('[data-mcomponent="TextArea"], [data-mcomponent="ServerTextArea"]');
        // If MComponent attributes not present, fall back to links and spans
        if (els.length === 0) {
            els = document.querySelectorAll('a, span[role="link"], [role="button"]');
        }

        for (var i = 0; i < els.length; i++) {
            var el = els[i];
            if (el.closest('[data-filtered]')) continue;
            var text = el.textContent || '';
            var trimmed = text.trim();
            var h = el.getBoundingClientRect().height;

            // ADS: short element containing "Ad" (not "Add", not "Ads Manager")
            if (trimmed === 'Ad' || trimmed === 'Sponsored' ||
                (trimmed.indexOf('Ad') !== -1 && trimmed.length < 20 &&
                 trimmed.indexOf('Add') === -1 && trimmed.indexOf('Ads') === -1)) {
                var container = findContainer(el, 200, 1500);
                if (container) hide(container, 'ad');
                continue;
            }

            // STORIES
            if (trimmed === 'Create story') {
                var p = el;
                var found = false;
                for (var k = 0; k < 15; k++) {
                    if (!p.parentElement) break;
                    p = p.parentElement;
                    var pStyle = p.getAttribute('style') || '';
                    if (pStyle.indexOf('height') !== -1 && p.getBoundingClientRect().height > 100 &&
                        p.getBoundingClientRect().height < 500) {
                        found = true;
                        break;
                    }
                }
                if (found) hideCollapse(p, 'stories');
                continue;
            }

            // REELS
            if ((trimmed === 'Reels' || trimmed.indexOf('Reels') !== -1) && trimmed.length < 30) {
                var p = el;
                var found = false;
                for (var k = 0; k < 25; k++) {
                    if (!p.parentElement) break;
                    p = p.parentElement;
                    var ph = p.getBoundingClientRect().height;
                    if (ph > 400 && ph < window.innerHeight * 2 && p.children.length >= 2) {
                        found = true;
                        break;
                    }
                }
                if (found) hideCollapse(p, 'reels');
                continue;
            }

            // GROUP SUGGESTIONS: element text is exactly "Join"
            if (trimmed === 'Join') {
                var container = findContainer(el, 200, 1500);
                if (container) hide(container, 'group');
                continue;
            }

            // PAGE SUGGESTIONS: element text is exactly "Follow"
            if (trimmed === 'Follow') {
                var container = findContainer(el, 200, 1500);
                if (container) hide(container, 'page');
                continue;
            }

            // SUGGESTIONS
            if (trimmed === 'People you may know' || trimmed === 'Suggested for you') {
                var container = findContainer(el, 200, 600);
                if (container) hide(container, 'suggestion');
                continue;
            }

            // INTEREST PROMPTS
            if (trimmed === 'Are you interested in this post?') {
                var container = findContainer(el, 50, 300);
                if (container) hide(container, 'interest');
                continue;
            }

            // OPEN APP BANNER
            if (trimmed === 'Open app') {
                var container = findContainer(el, 30, 200);
                if (container) hide(container, 'banner');
                continue;
            }
        }

        // Also scan by aria-label which WebLite uses
        var labeled = document.querySelectorAll('[aria-label*="Sponsored"], [aria-label*="Join group"], [aria-label*="Follow"]');
        for (var i = 0; i < labeled.length; i++) {
            var el = labeled[i];
            if (el.closest('[data-filtered]')) continue;
            var label = el.getAttribute('aria-label') || '';
            if (label.indexOf('Sponsored') !== -1) {
                var container = findContainer(el, 200, 1500);
                if (container) hide(container, 'ad');
            }
        }

        var stats = {
            ads: document.querySelectorAll('[data-filtered="ad"]').length,
            stories: document.querySelectorAll('[data-filtered="stories"]').length,
            suggestions: document.querySelectorAll('[data-filtered="suggestion"]').length,
            groups: document.querySelectorAll('[data-filtered="group"]').length,
            pages: document.querySelectorAll('[data-filtered="page"]').length,
            banners: document.querySelectorAll('[data-filtered="banner"]').length
        };

        console.log('SLIMBOOK_STATS:' + JSON.stringify(stats));
        return stats;
    }

    // Run after a short delay to let WebSocket content render
    setTimeout(removeUnwanted, 1000);
    setTimeout(removeUnwanted, 3000);

    var observer = new MutationObserver(function() {
        setTimeout(removeUnwanted, 100);
    });
    observer.observe(document.body, { childList: true, subtree: true });

    window.__slimbook_filter = removeUnwanted;
    window.__slimbook_dump = function() {
        // Dump info about elements containing "Join" or "Follow"
        var all = document.querySelectorAll('*');
        var results = [];
        for (var i = 0; i < all.length; i++) {
            var el = all[i];
            // Only leaf-ish elements with short text
            if (el.children.length > 3) continue;
            var text = el.textContent?.trim() || '';
            if ((text === 'Join' || text === 'Follow' || text === 'Ad' || text === 'Sponsored' ||
                 text.indexOf('· Join') !== -1 || text.indexOf('· Follow') !== -1) && text.length < 200) {
                var rect = el.getBoundingClientRect();
                // Walk up 5 parents to show structure
                var parents = [];
                var p = el;
                for (var j = 0; j < 8; j++) {
                    if (!p.parentElement) break;
                    p = p.parentElement;
                    var pr = p.getBoundingClientRect();
                    parents.push(p.tagName + '.' + (p.className || '').substring(0,30) +
                        '[h=' + Math.round(pr.height) + ']' +
                        (p.getAttribute('data-mcomponent') ? ' mc=' + p.getAttribute('data-mcomponent') : '') +
                        (p.getAttribute('role') ? ' role=' + p.getAttribute('role') : ''));
                }
                results.push({
                    text: text.substring(0, 80),
                    tag: el.tagName,
                    class: (el.className || '').substring(0, 40),
                    role: el.getAttribute('role'),
                    h: Math.round(rect.height),
                    w: Math.round(rect.width),
                    parents: parents
                });
            }
        }
        console.log('SLIMBOOK_DUMP:' + JSON.stringify(results, null, 0));
        return results;
    };
    window.__slimbook_setHighlight = function(on) {
        window.__slimbook_highlight = on;
        var filtered = document.querySelectorAll('[data-filtered]');
        for (var i = 0; i < filtered.length; i++) {
            filtered[i].removeAttribute('data-filtered');
            filtered[i].style.display = '';
            filtered[i].style.outline = '';
            filtered[i].style.opacity = '';
        }
        highlightMode = on;
        removeUnwanted();
    };
})();

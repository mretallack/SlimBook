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
            // Also try to remove the element from flow entirely
            el.setAttribute('hidden', '');
        }
        el.setAttribute('data-filtered', type);
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
                if (found) hide(p, 'stories');
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
                if (found) hide(p, 'reels');
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
            var lower = trimmed.toLowerCase();
            if (lower === 'people you may know' || lower === 'suggested for you') {
                var container = findContainer(el, 200, 1500);
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

        // AUTHOR & GROUP DETECTION via Android bridge
        if (typeof Android !== 'undefined' && Android.reportAuthor) {
            var links = document.querySelectorAll('span[role="link"]');
            var processed = [];
            for (var i = 0; i < links.length; i++) {
                var link = links[i];
                if (link.closest('[data-filtered]')) continue;
                if (link.closest('[data-author-checked]')) continue;
                if (processed.indexOf(link) !== -1) continue;
                var name = link.textContent?.trim() || '';
                var lh = link.getBoundingClientRect().height;
                if (name.length < 3 || name.length > 80 || lh <= 0 || lh > 25) continue;

                // Find the post container for this link
                var postContainer = findContainer(link, 200, 2000);
                if (!postContainer || postContainer.getAttribute('data-author-checked')) continue;

                // Find all role=link spans in this post container
                var postLinks = postContainer.querySelectorAll('span[role="link"]');
                var candidates = [];
                for (var j = 0; j < postLinks.length; j++) {
                    var pl = postLinks[j];
                    var pn = pl.textContent?.trim() || '';
                    var ph = pl.getBoundingClientRect().height;
                    if (pn.length > 2 && pn.length < 80 && ph > 0 && ph < 25) {
                        candidates.push(pn);
                        processed.push(pl);
                    }
                    if (candidates.length >= 3) break;
                }

                // Determine group vs author
                // If 2+ candidates: first is likely group/page, second is author
                var groupName = '';
                var authorName = '';
                if (candidates.length >= 2) {
                    groupName = candidates[0];
                    authorName = candidates[1];
                } else if (candidates.length === 1) {
                    authorName = candidates[0];
                }

                if (authorName) {
                    postContainer.setAttribute('data-author-checked', authorName);
                    Android.reportAuthor(authorName);
                    if (groupName) Android.reportGroup(groupName);

                    if (Android.isBlocked(authorName) || (groupName && Android.isBlocked(groupName))) {
                        hide(postContainer, 'blocked');
                    }
                }
            }
        }

        var stats = {
            ads: document.querySelectorAll('[data-filtered="ad"]').length,
            stories: document.querySelectorAll('[data-filtered="stories"]').length,
            suggestions: document.querySelectorAll('[data-filtered="suggestion"]').length,
            groups: document.querySelectorAll('[data-filtered="group"]').length,
            pages: document.querySelectorAll('[data-filtered="page"]').length,
            blocked: document.querySelectorAll('[data-filtered="blocked"]').length,
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
        // Dump all short text elements to understand the post structure
        var all = document.querySelectorAll('[role="link"], a, span');
        var results = [];
        for (var i = 0; i < all.length; i++) {
            var el = all[i];
            var text = el.textContent?.trim() || '';
            // Short text that could be author names or timestamps
            if (text.length > 1 && text.length < 80 && el.children.length < 3) {
                var rect = el.getBoundingClientRect();
                // Only visible elements in the top portion of a post area
                if (rect.height > 0 && rect.height < 30 && rect.top > 0) {
                    results.push({
                        text: text.substring(0, 60),
                        tag: el.tagName,
                        role: el.getAttribute('role'),
                        h: Math.round(rect.height),
                        top: Math.round(rect.top)
                    });
                }
            }
            if (results.length > 50) break;
        }
        console.log('SLIMBOOK_DUMP:' + JSON.stringify(results));
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

// SlimBook Content Filter v1.0
// Removes ads, stories, suggestions, and app banners from web.facebook.com (WebLite)
(function() {
    var highlightMode = window.__slimbook_highlight || false;

    function hide(el, type) {
        if (!el || el.getAttribute('data-filtered')) return;
        if (highlightMode) {
            el.style.outline = '3px solid red';
            el.style.opacity = '0.4';
        } else {
            el.style.display = 'none';
        }
        el.setAttribute('data-filtered', type);
    }

    function findContainer(el, minH, maxH) {
        var parent = el;
        for (var i = 0; i < 20; i++) {
            if (!parent.parentElement) break;
            parent = parent.parentElement;
            var h = parent.getBoundingClientRect().height;
            if (h >= minH && h <= maxH && parent.getAttribute('data-mcomponent') === 'MContainer') {
                return parent;
            }
        }
        return null;
    }

    function removeUnwanted() {
        var textAreas = document.querySelectorAll('[data-mcomponent="TextArea"], [data-mcomponent="ServerTextArea"]');

        for (var i = 0; i < textAreas.length; i++) {
            var el = textAreas[i];
            if (el.closest('[data-filtered]')) continue;
            var text = el.innerText || '';
            var trimmed = text.trim();

            // ADS
            if (text.indexOf('Ad') !== -1 && text.indexOf('Add ') === -1 &&
                text.indexOf('Ads Manager') === -1 && el.getBoundingClientRect().height < 25 && text.length < 80) {
                var container = findContainer(el, 200, 900);
                if (container) hide(container, 'ad');
            }

            // STORIES
            if (trimmed === 'Create story') {
                var container = findContainer(el, 100, 300);
                if (container) hide(container, 'stories');
            }

            // SUGGESTIONS
            if (trimmed === 'People you may know' || trimmed === 'Suggested for you') {
                var container = findContainer(el, 200, 600);
                if (container) hide(container, 'suggestion');
            }

            // OPEN APP BANNER
            if (trimmed === 'Open app') {
                var parent = el;
                for (var j = 0; j < 3; j++) {
                    if (parent.parentElement) parent = parent.parentElement;
                }
                hide(parent, 'banner');
            }
        }

        // Also catch horizontal story scrollers
        var scrollers = document.querySelectorAll('[data-is-h-scrollable="true"]');
        for (var i = 0; i < scrollers.length; i++) {
            var scroller = scrollers[i];
            if (scroller.closest('[data-filtered]')) continue;
            if (scroller.innerText && scroller.innerText.indexOf('Create story') !== -1 &&
                scroller.getBoundingClientRect().height < 450) {
                hide(scroller, 'stories');
            }
        }

        var stats = {
            ads: document.querySelectorAll('[data-filtered="ad"]').length,
            stories: document.querySelectorAll('[data-filtered="stories"]').length,
            suggestions: document.querySelectorAll('[data-filtered="suggestion"]').length,
            banners: document.querySelectorAll('[data-filtered="banner"]').length
        };

        console.log('SLIMBOOK_STATS:' + JSON.stringify(stats));
        return stats;
    }

    removeUnwanted();
    var observer = new MutationObserver(function() { removeUnwanted(); });
    observer.observe(document.body, { childList: true, subtree: true });
    window.__slimbook_filter = removeUnwanted;
    window.__slimbook_setHighlight = function(on) {
        window.__slimbook_highlight = on;
        // Reset and re-apply
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
